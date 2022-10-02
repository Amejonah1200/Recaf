package me.coley.recaf.ui.docking;

import java.util.function.Supplier;

/**
 * Factory type for creating {@link DockTab}.
 *
 * @author Matt Coley
 */
public interface DockTabFactory<DT extends DockTab> extends Supplier<DT> {}
