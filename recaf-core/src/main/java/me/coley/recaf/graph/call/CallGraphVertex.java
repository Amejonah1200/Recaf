package me.coley.recaf.graph.call;

import me.coley.recaf.code.MemberSignature;
import me.coley.recaf.code.MethodInfo;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;

public interface CallGraphVertex {

	@Nonnull
	MethodInfo getMethodInfo();

	@Nonnull
	MemberSignature getSignature();

	Collection<CallGraphVertex> getCallers();

	Collection<Map.Entry<Integer, CallGraphVertex>> getCalls();

}
