package software.coley.recaf.ui.control.richtext.problem;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.TwoDimensional;
import software.coley.collections.Unchecked;
import software.coley.recaf.analytics.logging.DebuggingLogger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.ui.control.richtext.Editor;
import software.coley.recaf.ui.control.richtext.EditorComponent;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Tracking for problems to display in an {@link Editor}.
 *
 * @author Matt Coley
 */
public class ProblemTracking implements EditorComponent, Consumer<PlainTextChange> {
	private static final DebuggingLogger logger = Logging.get(ProblemTracking.class);
	private final List<ProblemInvalidationListener> listeners = new CopyOnWriteArrayList<>();
	private final NavigableMap<Integer, List<Problem>> problems = new TreeMap<>();
	private Editor editor;

	/**
	 * @param line
	 * 		Line number of problem.
	 *
	 * @return Problem on the line.
	 */
	@Nullable
	public List<Problem> getProblems(int line) {
		return problems.get(line);
	}

	/**
	 * @param problem
	 * 		Problem to add.
	 */
	public void add(@Nonnull Problem problem) {
		List<Problem> list = problems.computeIfAbsent(problem.line(), k -> new ArrayList<>());
		list.add(problem);
		Unchecked.checkedForEach(listeners, ProblemInvalidationListener::onProblemInvalidation,
				(listener, t) -> logger.error("Exception thrown when adding problem to tracking", t));
	}

	/**
	 * @param problem
	 * 		Exact instance of a problem to remove.
	 *
	 * @return {@code true} when the problem was removed.
	 * {@code false} when the problem instance was not contained in the problems map.
	 */
	public boolean removeByInstance(@Nonnull Problem problem) {
		List<Problem> list = problems.get(problem.line());
		if (list != null) {
			boolean updated = list.removeIf(p -> p == problem);
			if (updated)
				Unchecked.checkedForEach(listeners, ProblemInvalidationListener::onProblemInvalidation,
						(listener, t) -> logger.error("Exception thrown when removing problem from tracking", t));
			// if list empty, remove the entry
			if (list.isEmpty())
				problems.remove(problem.line());
			return updated;
		}
		return false;
	}

	/**
	 * @param line
	 * 		Line containing problem to remove.
	 *
	 * @return {@code true} when a problem at the line was removed.
	 * {@code false} when there was no problem at the line.
	 */
	public boolean removeByLine(int line) {
		boolean updated = problems.remove(line) != null;
		if (updated)
			Unchecked.checkedForEach(listeners, ProblemInvalidationListener::onProblemInvalidation,
					(listener, t) -> logger.error("Exception thrown when removing problem from tracking", t));
		return updated;
	}

	/**
	 * @param phase
	 * 		The phase to remove problems of.
	 *
	 * @return {@code true} when one or more problems matching the phase were removed.
	 */
	public boolean removeByPhase(@Nonnull ProblemPhase phase) {
		boolean updated = problems.values().removeIf(list -> list.removeIf(p -> p.phase() == phase));
		if (updated)
			Unchecked.checkedForEach(listeners, ProblemInvalidationListener::onProblemInvalidation,
					(listener, t) -> logger.error("Exception thrown when removing problems from tracking", t));
		return updated;
	}

	/**
	 * Clear all problems.
	 */
	public void clear() {
		problems.clear();
		Unchecked.checkedForEach(listeners, ProblemInvalidationListener::onProblemInvalidation,
				(listener, t) -> logger.error("Exception thrown when clearing problems from tracking", t));
	}

	/**
	 * @param listener
	 * 		Listener to add.
	 */
	public void addListener(@Nonnull ProblemInvalidationListener listener) {
		listeners.add(listener);
	}

	/**
	 * @param listener
	 * 		Listener to remove.
	 *
	 * @return {@code true} when listener was removed.
	 * {@code false} when listener was not present to begin with.
	 */
	public boolean removeListener(@Nonnull ProblemInvalidationListener listener) {
		return listeners.remove(listener);
	}

	/**
	 * @param level
	 * 		Problem level to filter problems by.
	 *
	 * @return List of problems matching the given level.
	 */
	@Nonnull
	public List<Problem> getProblemsByLevel(ProblemLevel level) {
		return getProblems(p -> p.level() == level);
	}

	/**
	 * @param phase
	 * 		Problem phase to filter problems by.
	 *
	 * @return List of problems matching the given phase.
	 */
	@Nonnull
	public List<Problem> getProblemsByPhase(ProblemPhase phase) {
		return getProblems(p -> p.phase() == phase);
	}

	/**
	 * @param filter
	 * 		Filter to pass problems through.
	 *
	 * @return List of problems matching the filter.
	 */
	@Nonnull
	public List<Problem> getProblems(Predicate<Problem> filter) {
		List<Problem> list = new ArrayList<>();
		problems.values().stream()
				.flatMap(Collection::stream)
				.filter(filter)
				.forEach(list::add);
		return list;
	}

	/**
	 * @return Map of problems.
	 */
	@Nonnull
	public NavigableMap<Integer, List<Problem>> getProblems() {
		return problems;
	}

	public List<Problem> getAllProblems() {
		List<Problem> list = new ArrayList<>();
		problems.values().forEach(list::addAll);
		return list;
	}

	@Override
	public void accept(PlainTextChange change) {
		// TODO: There are some edge cases where the tracking of problem indicators will fail, and we just
		//  delete them. Deleting an empty line before a line with an error will void it.
		//  I'm not really sure how to make a clean fix for that, but because the rest of
		//  it works relatively well I'm not gonna touch it for now.
		String insertedText = change.getInserted();
		String removedText = change.getRemoved();
		boolean lineInserted = insertedText.contains("\n");
		boolean lineRemoved = removedText.contains("\n");

		// Handle line removal/insertion.
		//
		// Some thoughts, you may ask why we do an "if" block for each, but not with else if.
		// Well, copy-pasting text does both. So we remove then insert for replacement.
		if (lineRemoved) {
			ReadOnlyStyledDocument<?, ?, ?> lastDocumentSnapshot = editor.getLastDocumentSnapshot();

			// Get line number and add +1 to make it 1-indexed
			int start = lastDocumentSnapshot.offsetToPosition(change.getPosition(), TwoDimensional.Bias.Backward).getMajor() + 1;

			// End line number needs +1 since it will include the next line due to inclusion of "\n"
			int end = lastDocumentSnapshot.offsetToPosition(change.getRemovalEnd(), TwoDimensional.Bias.Backward).getMajor() + 1;

			onLinesRemoved(start, end);
		}
		if (lineInserted) {
			CodeArea area = editor.getCodeArea();

			// Get line number and add +1 to make it 1-indexed
			int start = area.offsetToPosition(change.getPosition(), TwoDimensional.Bias.Backward).getMajor() + 1;

			// End line number doesn't need +1 since it will include the next line due to inclusion of "\n"
			int end = area.offsetToPosition(change.getInsertionEnd(), TwoDimensional.Bias.Backward).getMajor();

			onLinesInserted(start, end);
		}
	}

	protected void onLinesInserted(int startLine, int endLine) {
		logger.debugging(l -> l.trace("Lines inserted: {}-{}", startLine, endLine));
		TreeSet<Map.Entry<Integer, List<Problem>>> set =
				new TreeSet<>((o1, o2) -> Integer.compare(o2.getKey(), o1.getKey()));
		set.addAll(problems.entrySet());
		set.stream()
				.filter(e -> e.getKey() >= startLine)
				.forEach(e -> {
					int line = e.getKey();
					List<Problem> list = e.getValue();

					// Shift all problems down by the shift amount
					int shift = 1 + endLine - startLine;
					removeByLine(line);
					list.forEach(p -> {
						logger.debugging(l -> l.trace("Move problem '{}' down {} lines", p.message(), shift));
						add(p.withLine(line + shift));
					});
				});
	}

	protected void onLinesRemoved(int startLine, int endLine) {
		logger.debugging(l -> l.trace("Lines removed: {}-{}", startLine, endLine));

		// We will want to sort the order of removed lines so we
		TreeSet<Map.Entry<Integer, List<Problem>>> set = new TreeSet<>(Comparator.comparingInt(Map.Entry::getKey));
		set.addAll(problems.entrySet());
		set.stream()
				.filter(e -> e.getKey() >= startLine)
				.forEach(e -> {
					int line = e.getKey();
					List<Problem> list = e.getValue();

					// Shift all problems up by the shift amount
					int shift = endLine - startLine;
					removeByLine(line);

					// Don't add problem back if it's in the removed range
					list.stream()
							.filter(p -> p.line() < startLine || p.line() > endLine)
							.forEach(p -> {
								logger.debugging(l -> l.trace("Move problem '{}' up {} lines", p.message(), shift));
								add(p.withLine(line - shift));
							});
				});
	}

	@Override
	public void install(@Nonnull Editor editor) {
		this.editor = editor;
		clear();
		editor.getTextChangeEventStream().addObserver(this);
	}

	@Override
	public void uninstall(@Nonnull Editor editor) {
		this.editor = null;
		clear();
		editor.getTextChangeEventStream().removeObserver(this);
	}
}
