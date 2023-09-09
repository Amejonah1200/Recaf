package software.coley.recaf.workspace.io;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import me.coley.cafedude.classfile.VersionConstants;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.Info;
import software.coley.recaf.info.builder.*;
import software.coley.recaf.info.properties.builtin.IllegalClassSuspectProperty;
import software.coley.recaf.util.ByteHeaderUtil;
import software.coley.recaf.util.IOUtil;
import software.coley.recaf.util.android.AndroidXmlUtil;
import software.coley.recaf.util.io.ByteSource;
import software.coley.recaf.util.visitors.CustomAttributeCollectingVisitor;

import java.io.IOException;

/**
 * Basic implementation of the info importer.
 *
 * @author Matt Coley
 */
@ApplicationScoped
public class BasicInfoImporter implements InfoImporter {
	private static final Logger logger = Logging.get(BasicInfoImporter.class);
	private final ClassPatcher classPatcher;
	private final InfoImporterConfig config;

	@Inject
	public BasicInfoImporter(@Nonnull InfoImporterConfig config, @Nonnull ClassPatcher classPatcher) {
		this.classPatcher = classPatcher;
		this.config = config;
	}

	@Nonnull
	@Override
	public Info readInfo(String name, ByteSource source) throws IOException {
		byte[] data = source.readAll();

		// Check for Java classes
		if (matchesClass(data)) {
			try {
				// Patch if not compatible with ASM
				if (!isAsmCompliantClass(data)) {
					byte[] patched = classPatcher.patch(name, data);

					// Ensure the patch was successful
					if (!isAsmCompliantClass(patched)) {
						logger.error("CafeDude patching output is still non-compliant with ASM for file: {}", name);
						return new FileInfoBuilder<>()
								.withRawContent(data)
								.withName(name)
								.build();
					} else {
						logger.debug("CafeDude patched class: {}", name);
						return new JvmClassInfoBuilder(new ClassReader(patched))
								.build();
					}
				}

				// Yield class
				return new JvmClassInfoBuilder(new ClassReader(data)).build();
			} catch (Throwable t) {
				// Invalid class, either some new edge case we need to add to CafeDude, or the file
				// isn't a class, but the structure models it close enough to look like one at a glance.
				// It may be unpacked later.
				return new FileInfoBuilder<>()
						.withRawContent(data)
						.withName(name)
						.withProperty(IllegalClassSuspectProperty.INSTANCE)
						.build();
			}
		}

		// Comparing against known file types.
		if (ByteHeaderUtil.match(data, ByteHeaderUtil.DEX)) {
			return new DexFileInfoBuilder()
					.withRawContent(data)
					.withName(name)
					.build();
		} else if (ByteHeaderUtil.match(data, ByteHeaderUtil.MODULES)) {
			return new ModulesFileInfoBuilder()
					.withRawContent(data)
					.withName(name)
					.build();
		} else if (name.toUpperCase().endsWith(".ARSC") &&
				ByteHeaderUtil.match(data, ByteHeaderUtil.ARSC)) {
			return new ArscFileInfoBuilder()
					.withRawContent(data)
					.withName(name)
					.build();
		} else if (name.toUpperCase().endsWith(".XML") &&
				(ByteHeaderUtil.match(data, ByteHeaderUtil.BINARY_XML) || AndroidXmlUtil.hasXmlIndicators(data))) {
			return new BinaryXmlFileInfoBuilder()
					.withRawContent(data)
					.withName(name)
					.build();
		} else if (ByteHeaderUtil.matchAny(data, ByteHeaderUtil.IMAGE_HEADERS)) {
			return new ImageFileInfoBuilder()
					.withRawContent(data)
					.withName(name)
					.build();
		} else if (ByteHeaderUtil.matchAny(data, ByteHeaderUtil.AUDIO_HEADERS)) {
			return new AudioFileInfoBuilder()
					.withRawContent(data)
					.withName(name)
					.build();
		} else if (ByteHeaderUtil.matchAny(data, ByteHeaderUtil.VIDEO_HEADERS)) {
			return new VideoFileInfoBuilder()
					.withRawContent(data)
					.withName(name)
					.build();
		}

		// Check for ZIP containers (For ZIP/JAR/JMod/WAR)
		//  - While this is more common, some of the known file types may match 'ZIP' with
		//    our 'any-offset' condition we have here.
		//  - We need 'any-offset' to catch all ZIP cases, but it can match some of the file types
		//    above in some conditions, which means we have to check for it last.
		if (ByteHeaderUtil.matchAtAnyOffset(data, ByteHeaderUtil.ZIP)) {
			ZipFileInfoBuilder builder = new ZipFileInfoBuilder()
					.withRawContent(data)
					.withName(name);

			// Handle by file name if known, otherwise treat as regular ZIP.
			if (name == null) return builder.build();

			// Record name, handle extension to determine info-type
			String extension = IOUtil.getExtension(name);
			if (extension == null) return builder.build();
			return switch (extension.toUpperCase()) {
				case "JAR" -> builder.asJar().build();
				case "APK" -> builder.asApk().build();
				case "WAR" -> builder.asWar().build();
				case "JMOD" -> builder.asJMod().build();
				default -> builder.build();
			};
		}

		// TODO: Record content-type (for quick recognition of media and other common file types)
		//  - Don't need a million info-types for every possible content-type, just the edge cases
		//    that need to be handled by the Recaf API.
		//  - Everything else can be stored as a property.

		// No special case known for file, treat as generic file
		// Will be automatically mapped to a text file if the contents are all mappable characters.
		return new FileInfoBuilder<>()
				.withRawContent(data)
				.withName(name)
				.build();
	}

	/**
	 * Check if the byte array is prefixed by the class file magic header.
	 *
	 * @param content
	 * 		File content.
	 *
	 * @return If the content seems to be a class at a first glance.
	 */
	private static boolean matchesClass(byte[] content) {
		// Null and size check
		// The smallest valid class possible that is verifiable is 37 bytes AFAIK, but we'll be generous here.
		if (content == null || content.length <= 16)
			return false;

		// We want to make sure the 'magic' is correct.
		if (!ByteHeaderUtil.match(content, ByteHeaderUtil.CLASS))
			return false;

		// 'dylib' files can also have CAFEBABE as a magic header... Gee, thanks Apple :/
		// Because of this we'll employ some more sanity checks.
		// Version number must be non-zero
		int version = ((content[6] & 0xFF) << 8) + (content[7] & 0xFF);
		if (version < VersionConstants.JAVA1)
			return false;

		// Must include some constant pool entries.
		// The smallest number includes:
		//  - utf8  - name of current class
		//  - class - wrapper of prior
		//  - utf8  - name of object class
		//  - class - wrapper of prior`
		int cpSize = ((content[8] & 0xFF) << 8) + (content[9] & 0xFF);
		return cpSize >= 4;
	}

	/**
	 * Check if the class can be parsed by ASM.
	 *
	 * @param content
	 * 		The class file content.
	 *
	 * @return {@code true} if ASM can parse the class.
	 */
	private boolean isAsmCompliantClass(byte[] content) {
		// Skip validation if configured.
		if (config.doSkipAsmValidation())
			return true;

		// ASM should be able to write back the read class.
		try {
			CustomAttributeCollectingVisitor visitor = new CustomAttributeCollectingVisitor(new ClassWriter(0));
			ClassReader reader = new ClassReader(content);
			reader.accept(visitor, 0);
			if (visitor.hasCustomAttributes()) {
				throw new IllegalStateException("Unknown attributes found in class: " + reader.getClassName() + "[" +
						String.join(", ", visitor.getCustomAttributeNames()) + "]");
			}
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	@Nonnull
	@Override
	public String getServiceId() {
		return SERVICE_ID;
	}

	@Nonnull
	@Override
	public InfoImporterConfig getServiceConfig() {
		return config;
	}
}
