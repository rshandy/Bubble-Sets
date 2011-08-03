/**
 * 
 */
package setvis.shape;

import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import setvis.Group;
import setvis.SetOutline;

/**
 * Generates a {@link Shape} for the vertices generated by
 * {@link SetOutline#createOutline(Rectangle2D[], Rectangle2D[])}.
 * 
 * @author Joschi <josua.krause@googlemail.com>
 * 
 */
public abstract class AbstractShapeCreator {

	/**
	 * The number of threads used by the parallel shape creation methods.
	 * 
	 * @see #createShapesInParallel(Collection)
	 * @see #createShapesInParallel(Group[])
	 * @see #createShapesForListsInParallel(Collection)
	 */
	public static int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

	/**
	 * Whether to automatically use parallel set creation.
	 */
	public static boolean AUTO_PARALLEL = true;

	/**
	 * The generator for the vertices of the sets.
	 */
	private final SetOutline setOutline;

	/**
	 * Creates an {@link AbstractShapeCreator} with a given set outline creator.
	 * 
	 * @param setOutline
	 *            The creator of the set outlines.
	 */
	public AbstractShapeCreator(final SetOutline setOutline) {
		this.setOutline = setOutline;
		// proper initialization of the radius
		setRadius(10.0);
	}

	/**
	 * @return The underlying outline generator.
	 */
	public SetOutline getSetOutline() {
		return setOutline;
	}

	/**
	 * The radius that should be added to the rectangles for the outline
	 * creation.
	 */
	private double radius;

	/**
	 * @param radius
	 *            Sets the radius that should be added to the rectangles for the
	 *            outline creation.
	 */
	public void setRadius(final double radius) {
		this.radius = radius;
	}

	/**
	 * @return The radius which is added to the rectangles for the outline
	 *         creation.
	 */
	public double getRadius() {
		return radius;
	}

	/**
	 * Creates shapes for all sets given by {@code items}.
	 * 
	 * @param items
	 *            A collection of sets. The sets are themselves a collection of
	 *            rectangles.
	 * @return The outline shapes for each set given.
	 */
	public final Shape[] createShapesForLists(
			final Collection<? extends Collection<Rectangle2D>> items) {
		final List<Rectangle2D[]> list = new LinkedList<Rectangle2D[]>();
		for (final Collection<Rectangle2D> group : items) {
			list.add(group.toArray(new Rectangle2D[group.size()]));
		}
		return createShapesFor(list);
	}

	/**
	 * Creates shapes for all sets given by {@code items}.
	 * 
	 * @param items
	 *            A collection of sets. The sets are themselves an array of
	 *            rectangles.
	 * @return The outline shapes for each set given.
	 */
	public final Shape[] createShapesFor(final Collection<Rectangle2D[]> items) {
		if (AUTO_PARALLEL) {
			return createShapesInParallel(items);
		}
		final Shape[] res = new Shape[items.size()];
		int i = 0;
		for (final Rectangle2D[] group : items) {
			res[i] = createShapeFor(group, getNonMembers(items, i));
			i++;
		}
		return res;
	}

	/**
	 * Creates shapes for all sets given by {@code groups}.
	 * 
	 * @param groups
	 *            A collection of groups.
	 * @return The outline shapes for each set given.
	 */
	public final Shape[] createShapesForGroups(final Collection<Group> groups) {
		final int size = groups.size();
		if (AUTO_PARALLEL) {
			return createShapesInParallel(groups.toArray(new Group[size]));
		}
		final Shape[] res = new Shape[size];
		int i = 0;
		for (final Group group : groups) {
			res[i] = createShapeFor(group, getNonMembersForGroups(groups, i));
			i++;
		}
		return res;
	}

	/**
	 * Creates shapes for all sets given by {@code rects} in parallel using
	 * {@link #THREAD_COUNT} number of threads.
	 * 
	 * @param rects
	 *            A collection of groups. The sets are themselves an array of
	 *            rectangles.
	 * @return The outline shapes for each set given.
	 */
	public final Shape[] createShapesForListsInParallel(
			final Collection<? extends Collection<Rectangle2D>> rects) {
		final Group[] groups = new Group[rects.size()];
		int i = 0;
		for (final Collection<Rectangle2D> group : rects) {
			groups[i++] = new Group(group);
		}
		return createShapesInParallel(groups);
	}

	/**
	 * Creates shapes for all sets given by {@code rects} in parallel using
	 * {@link #THREAD_COUNT} number of threads.
	 * 
	 * @param rects
	 *            A collection of groups. The sets are themselves a collection
	 *            of rectangles.
	 * @return The outline shapes for each set given.
	 */
	public final Shape[] createShapesInParallel(
			final Collection<Rectangle2D[]> rects) {
		final Group[] groups = new Group[rects.size()];
		int i = 0;
		for (final Rectangle2D[] group : rects) {
			groups[i++] = new Group(Arrays.asList(group));
		}
		return createShapesInParallel(groups);
	}

	/**
	 * Creates shapes for all sets given by {@code groups} in parallel using
	 * {@link #THREAD_COUNT} number of threads.
	 * 
	 * @param groups
	 *            An array of groups.
	 * @return The outline shapes for each set given.
	 */
	public final Shape[] createShapesInParallel(final Group[] groups) {
		final Collection<Group> list = Arrays.asList(groups);
		final int count = THREAD_COUNT;
		final Thread[] workers = new Thread[count];
		final Shape[] shapes = new Shape[groups.length];
		int tc = count;
		while (--tc >= 0) {
			final int i = tc;
			final Thread w = new Thread() {
				@Override
				public void run() {
					for (int pos = i; pos < groups.length; pos += count) {
						shapes[pos] = createShapeFor(groups[pos],
								getNonMembersForGroups(list, pos));
					}
				}
			};
			workers[i] = w;
			w.start();
		}
		try {
			for (final Thread w : workers) {
				w.join();
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return shapes;
	}

	/**
	 * Finds all items not belonging to the given group.
	 * 
	 * @param items
	 *            A collection of sets. The sets are themselves an array of
	 *            rectangles.
	 * @param groupID
	 *            The group.
	 * @return All items not belonging to the group.
	 */
	private static Rectangle2D[] getNonMembers(
			final Collection<Rectangle2D[]> items, final int groupID) {
		final List<Rectangle2D> res = new LinkedList<Rectangle2D>();
		int g = 0;
		for (final Rectangle2D[] group : items) {
			if (g++ == groupID) {
				continue;
			}
			res.addAll(Arrays.asList(group));
		}
		return res.toArray(new Rectangle2D[res.size()]);
	}

	/**
	 * Finds all rectangles not belonging to the given group.
	 * 
	 * @param items
	 *            A collection of groups.
	 * @param groupID
	 *            The group.
	 * @return All rectangles not belonging to the group.
	 */
	private static Rectangle2D[] getNonMembersForGroups(
			final Collection<Group> items, final int groupID) {
		final List<Rectangle2D> res = new LinkedList<Rectangle2D>();
		int g = 0;
		for (final Group group : items) {
			if (g++ == groupID) {
				continue;
			}
			res.addAll(Arrays.asList(group.rects));
		}
		return res.toArray(new Rectangle2D[res.size()]);
	}

	/**
	 * Creates a shape for the given set avoiding the given items not contained
	 * in the set.
	 * 
	 * @param members
	 *            The items representing the set.
	 * @param nonMembers
	 *            The items excluded from the set.
	 * @return The resulting shape.
	 */
	public final Shape createShapeFor(final Rectangle2D[] members,
			final Rectangle2D[] nonMembers) {
		return createShapeFor(members, nonMembers, null);
	}

	/**
	 * Creates a shape for the given set avoiding the given items not contained
	 * in the set and guided by optional lines.
	 * 
	 * @param members
	 *            The items representing the set.
	 * @param nonMembers
	 *            The items excluded from the set.
	 * @param lines
	 *            Optional lines that may be ignored.
	 * @return The resulting shape.
	 */
	public final Shape createShapeFor(final Rectangle2D[] members,
			final Rectangle2D[] nonMembers, final Line2D[] lines) {
		final Rectangle2D[] m = mapRects(members);
		final Rectangle2D[] n = mapRects(nonMembers);
		final Point2D[] res = setOutline.createOutline(m, n, lines);
		return convertToShape(res);
	}

	/**
	 * Creates a shape for the given set avoiding the given groups not contained
	 * in the set.
	 * 
	 * @param group
	 *            The group.
	 * @param nonMembers
	 *            The items excluded from the set.
	 * @return The resulting shape.
	 */
	public final Shape createShapeFor(final Group group,
			final Rectangle2D[] nonMembers) {
		return createShapeFor(group.rects, nonMembers, group.lines);
	}

	/**
	 * Maps rectangles by performing {@link #mapRect(Rectangle2D)} on each
	 * element of the array.
	 * 
	 * @param rects
	 *            The array to map.
	 * @return A new array containing the mapped rectangles.
	 */
	protected final Rectangle2D[] mapRects(final Rectangle2D[] rects) {
		int i = rects.length;
		final Rectangle2D[] res = new Rectangle2D[i];
		while (--i >= 0) {
			res[i] = mapRect(rects[i]);
		}
		return res;
	}

	/**
	 * Maps one rectangle. The current map is just the identity but it can be
	 * changed by overwriting it in subclasses.
	 * 
	 * @param r
	 *            The rectangle to map.
	 * @return The new mapped rectangle. This method has to guarantee that the
	 *         returned rectangle is newly created.
	 */
	protected final Rectangle2D mapRect(final Rectangle2D r) {
		final double radius = getRadius();
		final double dblRad = radius * 2.0;
		return new Rectangle2D.Double(r.getMinX() - radius, r.getMinY()
				- radius, r.getWidth() + dblRad, r.getHeight() + dblRad);
	}

	/**
	 * Converts vertices to a shape.
	 * 
	 * @param points
	 *            The sorted vertices representing the outlines of a set.
	 * @return The resulting shape.
	 */
	protected abstract Shape convertToShape(Point2D[] points);

}
