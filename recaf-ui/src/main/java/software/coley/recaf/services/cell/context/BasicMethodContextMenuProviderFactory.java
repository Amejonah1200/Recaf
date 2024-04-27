package software.coley.recaf.services.cell.context;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javafx.scene.control.ContextMenu;

import static org.kordamp.ikonli.carbonicons.CarbonIcons.*;

import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.ClassInfo;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.info.member.MethodMember;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.path.IncompletePathException;
import software.coley.recaf.path.PathNodes;
import software.coley.recaf.services.cell.icon.IconProvider;
import software.coley.recaf.services.cell.icon.IconProviderService;
import software.coley.recaf.services.cell.text.TextProvider;
import software.coley.recaf.services.cell.text.TextProviderService;
import software.coley.recaf.services.navigation.Actions;
import software.coley.recaf.services.search.match.StringPredicateProvider;
import software.coley.recaf.ui.contextmenu.ContextMenuBuilder;
import software.coley.recaf.ui.pane.search.MemberReferenceSearchPane;
import software.coley.recaf.util.ClipboardUtil;
import software.coley.recaf.util.Unchecked;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.ClassBundle;
import software.coley.recaf.workspace.model.bundle.JvmClassBundle;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import java.util.List;

import static software.coley.recaf.util.Menus.action;

/**
 * Basic implementation for {@link MethodContextMenuProviderFactory}.
 *
 * @author Matt Coley
 */
@ApplicationScoped
public class BasicMethodContextMenuProviderFactory extends AbstractContextMenuProviderFactory implements MethodContextMenuProviderFactory {
	private static final Logger logger = Logging.get(BasicMethodContextMenuProviderFactory.class);

	@Inject
	public BasicMethodContextMenuProviderFactory(@Nonnull TextProviderService textService,
												 @Nonnull IconProviderService iconService,
												 @Nonnull Actions actions) {
		super(textService, iconService, actions);
	}

	@Nonnull
	@Override
	public ContextMenuProvider getMethodContextMenuProvider(@Nonnull ContextSource source,
															@Nonnull Workspace workspace,
															@Nonnull WorkspaceResource resource,
															@Nonnull ClassBundle<? extends ClassInfo> bundle,
															@Nonnull ClassInfo declaringClass,
															@Nonnull MethodMember method) {
		return () -> {
			TextProvider nameProvider = textService.getMethodMemberTextProvider(workspace, resource, bundle, declaringClass, method);
			IconProvider iconProvider = iconService.getClassMemberIconProvider(workspace, resource, bundle, declaringClass, method);
			ContextMenu menu = new ContextMenu();
			addHeader(menu, nameProvider.makeText(), iconProvider.makeIcon());
			var builder = new ContextMenuBuilder(menu, source).forMember(workspace, resource, bundle, declaringClass, method);

			if (source.isReference()) {
				builder.item("menu.goto.method", ARROW_RIGHT , () -> {
							ClassPathNode classPath = PathNodes.classPath(workspace, resource, bundle, declaringClass);
							try {
								actions.gotoDeclaration(classPath)
										.requestFocus(method);
							} catch (IncompletePathException ex) {
								logger.error("Cannot go to method due to incomplete path", ex);
							}
						});
			} else {
				builder.item("menu.tab.copypath", COPY_LINK, () -> ClipboardUtil.copyString(declaringClass, method));
				builder.item("menu.edit.assemble.method", EDIT, Unchecked.runnable(() ->
						actions.openAssembler(PathNodes.memberPath(workspace, resource, bundle, declaringClass, method))
				));

				if (declaringClass.isJvmClass()) {
					JvmClassBundle jvmBundle = (JvmClassBundle) bundle;
					JvmClassInfo declaringJvmClass = declaringClass.asJvmClass();

					builder.item("menu.edit.copy", COPY_FILE, () -> actions.copyMember(workspace, resource, jvmBundle,declaringJvmClass, method));
					builder.item("menu.edit.noop", CIRCLE_DASH, () -> actions.makeMethodsNoop(workspace, resource, jvmBundle, declaringJvmClass, List.of(method)));
					builder.item("menu.edit.delete", TRASH_CAN, () -> actions.deleteClassMethods(workspace, resource, jvmBundle, declaringJvmClass, List.of(method)));
				}

				// TODO: implement additional operations
				//  - Edit
				//    - Add annotation
				//    - Remove annotations
			}

			// Search actions
			builder.item("menu.search.method-references", CODE, () -> {
				MemberReferenceSearchPane pane = actions.openNewMemberReferenceSearch();
				pane.ownerPredicateIdProperty().setValue(StringPredicateProvider.KEY_EQUALS);
				pane.namePredicateIdProperty().setValue(StringPredicateProvider.KEY_EQUALS);
				pane.descPredicateIdProperty().setValue(StringPredicateProvider.KEY_EQUALS);
				pane.ownerValueProperty().setValue(declaringClass.getName());
				pane.nameValueProperty().setValue(method.getName());
				pane.descValueProperty().setValue(method.getDescriptor());
			});

			// Documentation actions
			builder.memberItem("menu.analysis.comment", ADD_COMMENT, actions::openCommentEditing);

			// Refactor actions
			builder.memberItem("menu.refactor.rename", TAG_EDIT, actions::renameMethod);

			// TODO: implement additional operations
			//  - View
			//    - Control flow graph
			//    - Application flow graph
			//  - Deobfuscate
			//    - Regenerate variable names
			//    - Optimize with pattern matchers
			//    - Optimize with SSVM
			//  - Simulate with SSVM (Virtualize > Run)
			return menu;
		};
	}
}
