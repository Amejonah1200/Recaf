package me.coley.recaf.assemble.ast.arch;

import me.coley.recaf.assemble.ast.BaseElement;
import me.coley.recaf.assemble.ast.PrintContext;
import me.coley.recaf.assemble.ast.meta.Signature;

import java.util.ArrayList;
import java.util.List;

/**
 * Common base implementation for definition types.
 *
 * @author Matt Coley
 * @author Justus Garbe
 */
public abstract class AbstractDefinition extends BaseElement implements Definition {
	private final List<Annotation> annotations = new ArrayList<>();
	private Modifiers modifiers;
	private Signature signature;

	@Override
	public Modifiers getModifiers() {
		return modifiers;
	}

	@Override
	public List<Annotation> getAnnotations() {
		return annotations;
	}

	@Override
	public Signature getSignature() {
		return signature;
	}

	public void addAnnotation(Annotation annotation) {
		annotations.add(annotation);
	}

	public void setModifiers(Modifiers modifiers) {
		this.modifiers = modifiers;
	}

	public void setAnnotations(List<Annotation> annotations) {
		this.annotations.clear();
		this.annotations.addAll(annotations);
	}

	protected String buildDefString(PrintContext context, String type) {
		StringBuilder sb = new StringBuilder();
		if(signature != null)
			sb.append(signature.print(context));
		for (Annotation annotation : getAnnotations())
			sb.append(annotation.print(context));
		sb.append(type).append(" ");
		if (getModifiers().value() > 0) {
			sb.append(getModifiers().print(context).toLowerCase()).append(' ');
		}
		return sb.toString();
	}

	public void setSignature(Signature signature) {
		this.signature = signature;
	}
}
