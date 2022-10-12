package org.openlca.app.tools.graphics.layouts;

import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.openlca.app.tools.graphics.model.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.eclipse.draw2d.PositionConstants.*;


/**
 * TreeLayout computes the location of the nodes of a vertex/edge tree.
 * The tree can be oriented in two ways: left to right for the output side of
 * the reference/root node and right to left for the input side.
 * For the theory behind this algorithm, see:
 * <ul>
 * <li><a href="https://www.researchgate.net/publication/30508504_Improving_Walker%27s_Algorithm_to_Run_in_Linear_Time">Improving Walker's Algorithm to Run in Linear Time</a>,</li>
 * <li><a href="http://www.cs.unc.edu/techreports/89-034.pdf">A Node-Positioning
 * Algorithm for General Trees</a>,</li>
 * <li><a href="https://github.com/prefuse/Prefuse/blob/master/src/prefuse/action/layout/graph/NodeLinkTreeLayout.java">Prefuse on Github</a>.</li>
 * </ul>
 *
 * The product system being a graph, the tree algorithm is not enough to lay
 * out every linked nodes of it. For that purpose, a node can have a
 * mistletoe. A mistletoe is a branch of the tree that does not expand in the
 * direction. For example, a provider of the reference node can also be the
 * provider of a node that does not chain with the reference node.
 * To better understand how the algorithm works, let's take a reference
 * node (A), two providers (B and C) and a secondary recipient of B (D).
 * To expand the node B, the providers B and C are shifted from a depth of 2
 * to a depth of 3.
 * <pre>
 *
 *                       2     1      3     2     1
 *                       B ━┓         B ━━━ D ━┓
 *                          ┣━ A  =>           ┣━ A
 *                       C ━┛         C ━━━━━━━┛
 *</pre>
 * The node B has a mistletoe D. In order to determine the depths of the nodes
 * of the main tree, the mistletoes' depths should be computed (first walks
 * of the mistletoes). Then the position of each subtree of the main tree are
 * determined by the first and second walk. Once, the positions of each node
 * of the main tree are determined, the final positions of the apex are thus
 * fixed such that the final positions of the mistletoes can be computed
 * (second walks of the mistletoe).
 */
public class TreeLayout {

	/**
	 * Direction of the graph NORTH, SOUTH, EAST OR WEST.
	 * */
	private final int orientation;

	/**
	 * The reference node of the tree (to differentiate with the root of a
	 * subtree).
	 */
	protected final Vertex apexVertex;

	/**
	 * Size of the levels (width of the columns in our case) of the tree.
	 * The index in this array is the depth of the vertex (the root is at depth
	 * 0).
	 */
	private final List<Double> levelSizes;
	private final List<Double> mistletoeSizes;
	private final GraphLayout manager;
	private final boolean forInputs;
	/**
	 * The maximum depth of the tree (set to zero if there is only an apex
	 * node).
	 */
	private int maxDepth = 0;
	private List<Double> levels;

	TreeLayout(	GraphLayout manager, int orientation, Component apex,
						 boolean forInputs) {
		this.manager = manager;
		this.orientation = orientation;
		this.forInputs = forInputs;
		apexVertex = createApexVertex(apex);
		createTree(apexVertex, 0);
		if (apexVertex != null)
			createMistletoes(apexVertex);
		levelSizes = new ArrayList<>(Collections.nCopies(maxDepth + 2, 0.0));
		mistletoeSizes = new ArrayList<>(Collections.nCopies(maxDepth + 2, 0.0));
	}

	private Vertex createApexVertex(Component apex) {
		var figure = manager.figureOf(apex);
		var size = manager.getConstrainedSize(figure);
		var vertex = new Vertex(apex, figure, size, 0);
		if (vertex.figure == null
				|| manager.getConstraint(vertex.figure) == null)  // see layout()
			return null;

		// Only the first Vertex of node is added to the map.
		manager.mapNodeToVertex.putIfAbsent(apex, vertex);
		return vertex;
	}

	/**
	 * Calculate the center location of the vertex.
	 */
	private Point calculateStartLocation(Vertex vertex) {
		var previousVertex = manager.mapNodeToVertex.get(vertex.node);
		if (previousVertex != null)
			return previousVertex.startLocation;

		var constraint = (Rectangle) manager.getConstraint(vertex.figure);
		if (constraint == null) {
			return null;
		}

		// TODO Keep location from last layout
		// If the node has not been moved by the user. Its startLocation is null.
		return (constraint.getLocation().x == Integer.MAX_VALUE
				|| constraint.getLocation().y == Integer.MAX_VALUE)
				? null
				: constraint.getLocation().getTranslated(vertex.size.getScaled(0.5));
	}

	private Point calculateEndLocation(Vertex vertex) {
		var previousVertex = manager.mapNodeToVertex.get(vertex.node);
		return (previousVertex == null || previousVertex.endLocation == null)
				? vertex.startLocation
				: previousVertex.endLocation;
	}

	public void run() {
		// Set the locations of the apex vertex.
		setApexLocation();

		// Do the first passes of the mistletoes to determine the total level
		// sizes.
		firstWalkMistletoes(apexVertex, 1);

		// Do the first pass  of the main tree (assign preliminary y-coordinate).
		firstWalk(apexVertex, 0, 1);

		// Sum-up level sizes.
		determineLevels();

		// Do the second pass (assign the main tree final positions)
		secondWalk(apexVertex, null, -apexVertex.prelim, 0);

		// Do the mistletoes second pass
		secondWalkMistletoes(apexVertex);
	}

	private void secondWalkMistletoes(Vertex vertex) {
		if (vertex != apexVertex && vertex.mistletoe != null) {
			var apex = vertex.mistletoe.apexVertex;
			vertex.mistletoe.setApexLocation();
			vertex.mistletoe.secondWalk(apex, null, -apex.prelim, 0);

			// Once, the position of the apex is determined, the second walks on the
			// mistletoes of the mistletoe are run.
			vertex.mistletoe.secondWalkMistletoes(apex);
		}

		for (var child : vertex.children) {
			secondWalkMistletoes(child);
		}
	}

	private void setApexLocation() {
		var calculatedStartLocation = calculateStartLocation(apexVertex);
		apexVertex.startLocation = calculatedStartLocation == null
				? new Point(0, 0).getTranslated(apexVertex.size.getScaled(0.5))
				: calculatedStartLocation;
		apexVertex.endLocation = calculateEndLocation(apexVertex);
	}

	/**
	 * Determine the size of each level of the tree.
	 * Return the size of the last level for convenience.
	 */
	private Double determineLevels() {
		levels = new ArrayList<>(Collections.nCopies(maxDepth + 1, 0.0));
		for (int i = 1; i <= maxDepth; ++i)
			levels.set(i, levels.get(i - 1)
					+ (levelSizes.get(i + 1) + levelSizes.get(i)) / 2
					+ manager.distanceLevel
					+ mistletoeSizes.get(i + 1));
		return levels.get(levels.size() - 1);
	}

	private void firstWalkMistletoes(Vertex vertex, int depth) {
		if (vertex != apexVertex && vertex.mistletoe != null) {
			// First walks on the mistletoes of the mistletoe.
			vertex.mistletoe.firstWalkMistletoes(vertex.mistletoe.apexVertex, 1);

			// First walk on the mistletoe.
			vertex.mistletoe.firstWalk(vertex.mistletoe.apexVertex, 0, 1);
			mistletoeSizes.set(depth, Math.max(
					mistletoeSizes.get(depth), vertex.mistletoe.determineLevels()));
		}

		for (var child : vertex.children) {
			firstWalkMistletoes(child, depth + 1);
		}
	}

	/**
	 * Create the vertices of a tree which root is <code>parent</code>>.
	 * It works recursively by calling this method on children that have at
	 * least one child.
	 * @param parent The apex of the (sub-)tree to be created.
	 *
	 */
	private void createTree(Vertex parent, int depth) {
		var links = forInputs
				? parent.node.getAllTargetConnections()
				: parent.node.getAllSourceConnections();
		var children = new ArrayList<Component>();

		// Create the list of children of parent.
		for (var link : links) {
			var child = forInputs
					? link.getSourceNode()
					: link.getTargetNode();
			// Check if this child has not been already added by a neighbor, an
			// ancestor or the root of the subtree itself.
			if (!manager.mapNodeToVertex.containsKey(child) && !children.contains(child))
				children.add(child);
		}

		if (!children.isEmpty())
			maxDepth = Math.max(maxDepth, depth + 1);

		children.sort(Comparator.comparing(Component::getComparisonLabel));

		var parentSiblings = parent.parent == null
				? Collections.emptyList()
				: parent.parent.node.getSiblings(forInputs);

		// Removing children that are also siblings of the parent.
		var filteredChildren = children.stream()
				.filter(component -> !parentSiblings.contains(component))
				.toList();

		// Create the vertices of the filtered children.
		for (var child : filteredChildren) {
			var index = filteredChildren.indexOf(child);
			var figure = manager.figureOf(child);
			var size = manager.getConstrainedSize(figure);
			var childVertex = new Vertex(child, figure, size, index);
			childVertex.setParent(parent);
			if (childVertex.figure == null
					|| manager.getConstraint(childVertex.figure) == null)  // see layout()
				continue;
			childVertex.setStartLocation(calculateStartLocation(childVertex));
			if (filteredChildren.indexOf(child) != 0)
				childVertex.setPreviousSibling(
						manager.mapNodeToVertex.get(filteredChildren.get(index - 1)));

			manager.mapNodeToVertex.put(child, childVertex);
			parent.addChild(childVertex);
			createTree(childVertex, depth + 1);
		}
	}

	private void createMistletoes(Vertex parent) {
		for (var child : parent.children) {
			// Check if this vertex is a mistletoe.
			if (isMistletoe(child))
				child.mistletoe =
						new TreeLayout(manager, orientation, child.node, !forInputs);
			createMistletoes(child);
		}
	}

	/**
	 * In this first postorder walk, every node of the tree is assigned a
	 * preliminary x-coordinate (held in field Vertex.prelim). In addition,
	 * internal nodes are given modifiers, which will be used to move their
	 * offspring to the right (held in field Vertex.modifier).
	 * @param vertex
	 * @param number
	 * @param depth
	 */
	private void firstWalk(Vertex vertex, int number, int depth) {
		vertex.number = number;
		updateLevelSizes(depth, vertex);

		if (vertex.getChildCount() == 0) {  // is a leaf.
			vertex.prelim = vertex.previousSibling == null  // is the first child.
					? 0
					: vertex.previousSibling.prelim
					+ spacingOf(vertex.previousSibling, vertex, true);
		}
		else {
			var firstChild = vertex.getFirstChild();
			var lastChild = vertex.getLastChild();
			if (firstChild != null && lastChild != null) {
				var defaultAncestor = firstChild;
				var child = firstChild;
				for (int i = 0; child != null; ++i, child = child.getNextSibling()) {
					firstWalk(child, i, depth + 1);
					defaultAncestor = apportion(child, defaultAncestor);
				}

				executeShifts(vertex);

				var midpoint = (firstChild.prelim + lastChild.prelim) / 2;

				if (vertex.previousSibling != null) {  // is not the first child.
					vertex.prelim = vertex.previousSibling.prelim
							+ spacingOf(vertex.previousSibling, vertex, true);
					vertex.modifier = vertex.prelim - midpoint;
				} else {
					vertex.prelim = midpoint;
				}
			}
		}
	}

	private void executeShifts(Vertex vertex) {
		double shift = 0, change = 0;
		for (var child = vertex.getLastChild(); child != null;
				 child = child.previousSibling ) {
			child.prelim += shift;
			child.modifier += shift;
			change += child.change;
			shift += child.shift + change;
		}
	}

	/**
	 * This procedure cleans up the positioning of small sibling subtrees.
	 * When moving a new subtree farther and farther down, gaps may open up
	 * among smaller subtrees that were previously sandwiched between larger
	 * subtrees. Thus, when moving the new, larger subtree down, the distance it
	 * is moved is also apportioned to smaller, interior subtrees.
	 */
	private Vertex apportion(Vertex vertex, Vertex ancestor) {
		var previousSibling = vertex.previousSibling;
		if (previousSibling != null) {
			var downInsideVertex = vertex;
			var downOutsideVertex = vertex;
			var topInsideVertex = previousSibling;
			var topOutsideVertex = downInsideVertex.parent.getFirstChild();

			var downInsideShift = downInsideVertex.modifier;
			var downOutsideShift = downOutsideVertex.modifier;
			var topInsideShift = topInsideVertex.modifier;
			var topOutsideShift = topOutsideVertex.modifier;

			var nextBottom = nextBottom(topInsideVertex);
			var nextTop = nextTop(downInsideVertex);
			while ( nextBottom != null && nextTop != null ) {
				topInsideVertex = nextBottom;
				downInsideVertex = nextTop;
				topOutsideVertex = nextTop(topOutsideVertex);
				downOutsideVertex = nextBottom(downOutsideVertex);
				downOutsideVertex.ancestor = vertex;
				var shift = (topInsideVertex.prelim + topInsideShift)
						- (downInsideVertex.prelim + downInsideShift)
						+ spacingOf(topInsideVertex, downInsideVertex, false);
				if (shift > 0) {
					moveSubtree(ancestor(topInsideVertex, vertex, ancestor), vertex, shift);
					downInsideShift += shift;
					downOutsideShift += shift;
				}
				topInsideShift += topInsideVertex.modifier;
				downInsideShift += downInsideVertex.modifier;
				topOutsideShift += topOutsideVertex.modifier;
				downOutsideShift += downOutsideVertex.modifier;

				nextBottom = nextBottom(topInsideVertex);
				nextTop = nextTop(downInsideVertex);
			}
			if (nextBottom != null && nextBottom(downOutsideVertex) == null) {
				downOutsideVertex.thread = nextBottom;
				downOutsideVertex.modifier += topInsideShift - downOutsideShift;
			}
			if (nextTop != null && nextTop(topOutsideVertex) == null ) {
				topOutsideVertex.thread = nextTop;
				topOutsideVertex.modifier += downInsideShift - topOutsideShift;
				ancestor = vertex;
			}
		}
		return ancestor;
	}

	private void secondWalk(Vertex vertex, Vertex parent, double modifierSum,
													int depth) {
		var levelSign = ((orientation & NORTH_WEST) != 0 && forInputs)
				|| ((orientation & SOUTH_EAST) != 0 && !forInputs)
				? 1 : -1;
		var x = (orientation & (WEST | EAST)) != 0
				? (int) (apexVertex.endLocation.x
				+ Math.round(levels.get(depth)) * levelSign)
				: (int) (apexVertex.endLocation.x + vertex.prelim + modifierSum);
		var y = (orientation & (WEST | EAST)) != 0
				? (int) (apexVertex.endLocation.y + vertex.prelim + modifierSum)
				: (int) (apexVertex.endLocation.y
				+ Math.round(levels.get(depth)) * levelSign);
		var location = new Point(x, y);
		vertex.setLocation(location, parent);

		for (var child : vertex.children)
			secondWalk(child, vertex, modifierSum + vertex.modifier, depth + 1);
	}

	private Vertex nextTop(Vertex vertex) {
		var child = vertex.getFirstChild();
		return child != null ? child : vertex.thread;
	}

	private Vertex nextBottom(Vertex vertex) {
		var child = vertex.getLastChild();
		return child != null ? child : vertex.thread;
	}

	private void moveSubtree(Vertex wm, Vertex wp, double shift) {
		double subtrees = wp.number - wm.number;
		wp.change -= shift/subtrees;
		wp.shift += shift;
		wm.change += shift/subtrees;
		wp.prelim += shift;
		wp.modifier += shift;
	}

	private double spacingOf(Vertex left, Vertex right, boolean areSiblings) {
		var neighborSeparation = areSiblings
				? manager.distanceSibling :
				manager.distanceSubtree;
		var lengthsMean = (orientation & (NORTH | SOUTH)) != 0
				? (left.size.width + right.size.width) / 2
				: (left.size.height + right.size.height) / 2;
		return neighborSeparation + lengthsMean;
	}

	private Vertex ancestor(Vertex topInsideVertex, Vertex vertex, Vertex ancestor) {
		return (topInsideVertex.ancestor != null
				&& topInsideVertex.ancestor.parent == vertex.parent)
				? topInsideVertex.ancestor
				: ancestor;
	}

	private boolean isMistletoe(Vertex vertex) {
		var links = forInputs
				? vertex.node.getAllSourceConnections()
				: vertex.node.getAllTargetConnections();
		for (var link : links) {
			if (link.isCloseLoop())
				continue;

			var otherNode = forInputs
					? link.getTargetNode()
					: link.getSourceNode();
			if (!manager.mapNodeToVertex.containsKey(otherNode))
				return true;
		}
		return false;
	}

	private void updateLevelSizes(int depth, Vertex vertex) {
		double d = (orientation & (WEST | EAST)) != 0
				? vertex.size.width()
				: vertex.size.height();
		levelSizes.set(depth, Math.max(levelSizes.get(depth), d));
	}

}
