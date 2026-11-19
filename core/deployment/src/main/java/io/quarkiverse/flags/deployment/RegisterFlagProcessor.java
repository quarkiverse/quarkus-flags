package io.quarkiverse.flags.deployment;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.jandex.gizmo2.Jandex2Gizmo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkiverse.flags.BigDecimalValue;
import io.quarkiverse.flags.BooleanValue;
import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.IntValue;
import io.quarkiverse.flags.RegisterFlag;
import io.quarkiverse.flags.StringValue;
import io.quarkiverse.flags.WithEvaluator;
import io.quarkiverse.flags.WithMetadata;
import io.quarkiverse.flags.runtime.impl.ConfigFlagProvider;
import io.quarkiverse.flags.spi.ComponentOrder;
import io.quarkiverse.flags.spi.FlagEvaluator;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmo2Adaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ConstantBootstrapBuildItem;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.This;
import io.quarkus.gizmo2.creator.BlockCreator;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.groups.UniCreate;

public class RegisterFlagProcessor {

    private static final DotName REGISTER_FLAG = DotName.createSimple(RegisterFlag.class);
    private static final DotName WITH_METADATA = DotName.createSimple(WithMetadata.class);
    private static final DotName WITH_METADATA_LIST = DotName.createSimple(WithMetadata.List.class);
    private static final DotName WITH_EVALUATOR = DotName.createSimple(WithEvaluator.class);
    private static final DotName COMPUTATION_CONTEXT = DotName.createSimple(Flag.ComputationContext.class);
    private static final DotName FLAG_VALUE = DotName.createSimple(Flag.Value.class);

    private static final Set<DotName> SUPPORTED_TYPES = Set.of(
            DotName.createSimple(boolean.class),
            DotName.createSimple(Boolean.class),
            DotName.createSimple(int.class),
            DotName.createSimple(Integer.class),
            DotName.createSimple(String.class),
            DotName.createSimple(BigDecimal.class),
            FLAG_VALUE);

    @BuildStep
    void collectRegisteredFlags(CombinedIndexBuildItem index,
            BuildProducer<RegisteredFlagBuildItem> registeredFlags) {
        IndexView idx = index.getIndex();
        Set<String> seenNames = new HashSet<>();
        for (AnnotationInstance annotation : idx.getAnnotations(REGISTER_FLAG)) {
            validateAnnotationTarget(annotation);
            String flagName = resolveFlagName(annotation);
            if (!seenNames.add(flagName)) {
                throw new IllegalStateException(
                        "Duplicate @RegisterFlag name detected: " + flagName);
            }
            Map<String, String> metadata = collectMetadata(annotation.target());
            registeredFlags.produce(new RegisteredFlagBuildItem(
                    annotation.target().asDeclaration(), flagName, metadata));
        }
    }

    private void validateAnnotationTarget(AnnotationInstance annotation) {
        if (annotation.target().kind() == AnnotationTarget.Kind.FIELD) {
            FieldInfo field = annotation.target().asField();
            if (!Modifier.isStatic(field.flags())) {
                throw new IllegalStateException(
                        "@RegisterFlag is only supported on static fields: " + field.declaringClass().name() + "."
                                + field.name());
            }
            if (!SUPPORTED_TYPES.contains(field.type().name())) {
                throw new IllegalStateException(
                        "@RegisterFlag field has an unsupported type [" + field.type().name() + "]: "
                                + field.declaringClass().name() + "." + field.name());
            }
            if (!Modifier.isVolatile(field.flags()) && !Modifier.isFinal(field.flags())) {
                throw new IllegalStateException(
                        "@RegisterFlag field must be volatile or final: " + field.declaringClass().name() + "."
                                + field.name());
            }
        } else if (annotation.target().kind() == AnnotationTarget.Kind.METHOD) {
            MethodInfo method = annotation.target().asMethod();
            if (!Modifier.isStatic(method.flags())) {
                throw new IllegalStateException(
                        "@RegisterFlag is only supported on static methods: " + method.declaringClass().name()
                                + "." + method.name());
            }
            if (!SUPPORTED_TYPES.contains(method.returnType().name())) {
                throw new IllegalStateException(
                        "@RegisterFlag method has an unsupported return type [" + method.returnType().name()
                                + "]: " + method.declaringClass().name() + "." + method.name());
            }
            if (method.parametersCount() > 1
                    || (method.parametersCount() == 1
                            && !method.parameterType(0).name().equals(COMPUTATION_CONTEXT))) {
                throw new IllegalStateException(
                        "@RegisterFlag method must have no parameters or a single ComputationContext parameter: "
                                + method.declaringClass().name() + "." + method.name());
            }
        }
    }

    @BuildStep
    void generateRegisteredFlagProviders(List<RegisteredFlagBuildItem> registeredFlags,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<ConstantBootstrapBuildItem> constantBootstraps) {
        if (registeredFlags.isEmpty()) {
            return;
        }

        Map<String, List<RegisteredFlagBuildItem>> byClass = new LinkedHashMap<>();
        for (RegisteredFlagBuildItem item : registeredFlags) {
            String declaringClass = getDeclaringClassName(item);
            byClass.computeIfAbsent(declaringClass, k -> new ArrayList<>()).add(item);
        }

        ClassOutput classOutput = new GeneratedBeanGizmo2Adaptor(generatedBeans, constantBootstraps);
        Gizmo gizmo = Gizmo.create(classOutput);

        for (Map.Entry<String, List<RegisteredFlagBuildItem>> entry : byClass.entrySet()) {
            String declaringClass = entry.getKey();
            List<RegisteredFlagBuildItem> items = entry.getValue();
            String providerClassName = declaringClass + "_RegisteredFlagProvider";

            gizmo.class_(providerClassName, cc -> {
                This this_ = cc.this_();
                cc.addAnnotation(Singleton.class);
                cc.addAnnotation(Identifier.Literal.of(declaringClass));
                cc.addAnnotation(ComponentOrder.class, ac -> {
                    ac.addArray("before", new String[] { ConfigFlagProvider.ID });
                    ac.addArray("after", new String[] { InMemoryFlagProvider.ID });
                });
                cc.implements_(FlagProvider.class);

                FieldDesc flagsField = cc.field("flags", fc -> {
                    fc.setType(Uni.class);
                    fc.private_();
                    fc.final_();
                });

                cc.constructor(constructor -> {
                    constructor.public_();
                    constructor.body(bc -> {
                        bc.invokeSpecial(ConstructorDesc.of(Object.class), this_);
                        LocalVar flags = bc.localVar("flags", bc.new_(ArrayList.class));
                        for (RegisteredFlagBuildItem item : items) {
                            generateFlagBuilderCall(bc, this_, item, providerClassName, flags);
                        }
                        Expr uniCreate = bc.invokeStatic(
                                MethodDesc.of(Uni.class, "createFrom", UniCreate.class));
                        Expr uni = bc.invokeVirtual(
                                MethodDesc.of(UniCreate.class, "item", Uni.class, Object.class),
                                uniCreate, bc.invokeStatic(
                                        MethodDesc.of(List.class, "copyOf", List.class, java.util.Collection.class),
                                        flags));
                        bc.set(this_.field(flagsField), uni);
                        bc.return_();
                    });
                });

                cc.method("getFlags", mc -> {
                    mc.returning(Uni.class);
                    mc.public_();
                    mc.body(bc -> {
                        bc.return_(this_.field(flagsField));
                    });
                });

                cc.method("isCacheable", mc -> {
                    mc.returning(boolean.class);
                    mc.public_();
                    mc.body(bc -> {
                        bc.return_(Const.of(false));
                    });
                });
            });
        }
    }

    @BuildStep
    void transformFlagAccessSites(CombinedIndexBuildItem index,
            List<RegisteredFlagBuildItem> registeredFlags,
            BuildProducer<BytecodeTransformerBuildItem> transformers) {
        if (registeredFlags.isEmpty()) {
            return;
        }

        Map<String, FlagAccessInfo> fieldAccesses = new HashMap<>();
        Map<String, FlagAccessInfo> methodAccesses = new HashMap<>();
        Set<DotName> declaringClassNames = new HashSet<>();

        for (RegisteredFlagBuildItem item : registeredFlags) {
            if (item.getDeclaration().kind() == AnnotationTarget.Kind.FIELD) {
                FieldInfo field = item.getDeclaration().asField();
                String ownerInternal = field.declaringClass().name().toString().replace('.', '/');
                declaringClassNames.add(field.declaringClass().name());
                String key = ownerInternal + "." + field.name();
                fieldAccesses.put(key, new FlagAccessInfo(item.getFlagName(), field.type()));
            } else {
                MethodInfo method = item.getDeclaration().asMethod();
                String ownerInternal = method.declaringClass().name().toString().replace('.', '/');
                declaringClassNames.add(method.declaringClass().name());
                boolean hasCtx = method.parametersCount() == 1;
                String methodDescriptor = buildMethodDescriptor(method);
                String key = ownerInternal + "." + method.name() + methodDescriptor;
                methodAccesses.put(key, new FlagAccessInfo(item.getFlagName(), method.returnType(), hasCtx));
            }
        }

        IndexView idx = index.getIndex();
        Set<String> classesToTransform = new HashSet<>();
        for (DotName declaringClassName : declaringClassNames) {
            for (ClassInfo user : idx.getKnownUsers(declaringClassName)) {
                DotName userName = user.name();
                if (!declaringClassNames.contains(userName)
                        && !userName.toString().endsWith("_RegisteredFlagProvider")) {
                    classesToTransform.add(userName.toString());
                }
            }
        }

        for (String className : classesToTransform) {
            transformers.produce(new BytecodeTransformerBuildItem(className,
                    new RegisterFlagClassVisitorFunction(fieldAccesses, methodAccesses)));
        }
    }

    private void generateFlagBuilderCall(BlockCreator bc, This this_,
            RegisteredFlagBuildItem item, String providerClassName, LocalVar flags) {
        Expr builder = bc.invokeStatic(
                MethodDesc.of(Flag.class, "builder", Flag.Builder.class, String.class),
                Const.of(item.getFlagName()));

        if (item.getDeclaration().kind() == AnnotationTarget.Kind.FIELD) {
            FieldInfo field = item.getDeclaration().asField();
            builder = setBuilderValueFromField(bc, builder, field);
        } else {
            MethodInfo method = item.getDeclaration().asMethod();
            if (method.parametersCount() == 0) {
                builder = setBuilderValueFromMethod(bc, builder, method);
            } else {
                builder = setBuilderComputeFromMethod(bc, builder, method);
            }
        }

        builder = bc.invokeInterface(
                MethodDesc.of(Flag.Builder.class, "setOrigin", Flag.Builder.class, String.class),
                builder, Const.of(providerClassName));

        Map<String, String> metadata = item.getMetadata();
        if (!metadata.isEmpty()) {
            List<Expr> mapArgs = new ArrayList<>();
            for (Map.Entry<String, String> e : metadata.entrySet()) {
                mapArgs.add(Const.of(e.getKey()));
                mapArgs.add(Const.of(e.getValue()));
            }
            builder = bc.invokeInterface(
                    MethodDesc.of(Flag.Builder.class, "setMetadata", Flag.Builder.class, Map.class),
                    builder, bc.mapOf(mapArgs));
        }

        Expr flag = bc.invokeInterface(
                MethodDesc.of(Flag.Builder.class, "build", Flag.class),
                builder);
        bc.withList(flags).add(flag);
    }

    private Expr setBuilderValueFromField(BlockCreator bc, Expr builder, FieldInfo field) {
        DotName typeName = field.type().name();
        FieldDesc fieldDesc = Jandex2Gizmo.fieldDescOf(field);

        Expr computeFn = bc.lambda(Function.class, lc -> {
            lc.parameter("ctx", 0);
            lc.body(lb -> {
                Expr fieldValue = lb.getStaticField(fieldDesc);
                lb.return_(wrapFieldValue(lb, typeName, fieldValue));
            });
        });
        return bc.invokeInterface(
                MethodDesc.of(Flag.Builder.class, "setCompute", Flag.Builder.class, Function.class),
                builder, computeFn);
    }

    private Expr wrapFieldValue(BlockCreator bc, DotName typeName, Expr value) {
        if (isPrimitiveBooleanOrWrapper(typeName)) {
            return bc.invokeStatic(
                    MethodDesc.of(BooleanValue.class, "from", BooleanValue.class, boolean.class), value);
        } else if (isPrimitiveIntOrWrapper(typeName)) {
            return bc.new_(ConstructorDesc.of(IntValue.class, int.class), value);
        } else if (typeName.equals(DotName.createSimple(String.class))) {
            return bc.new_(ConstructorDesc.of(StringValue.class, String.class), value);
        } else if (typeName.equals(DotName.createSimple(BigDecimal.class))) {
            return bc.new_(ConstructorDesc.of(BigDecimalValue.class, BigDecimal.class), value);
        }
        return value;
    }

    private Expr setBuilderValueFromMethod(BlockCreator bc, Expr builder, MethodInfo method) {
        DotName typeName = method.returnType().name();
        MethodDesc methodDesc = Jandex2Gizmo.methodDescOf(method);

        Expr computeFn = bc.lambda(Function.class, lc -> {
            lc.parameter("ctx", 0);
            lc.body(lb -> {
                Expr result = lb.invokeStatic(methodDesc);
                lb.return_(wrapFieldValue(lb, typeName, result));
            });
        });
        return bc.invokeInterface(
                MethodDesc.of(Flag.Builder.class, "setCompute", Flag.Builder.class, Function.class),
                builder, computeFn);
    }

    private Expr setBuilderComputeFromMethod(BlockCreator bc, Expr builder, MethodInfo method) {
        DotName typeName = method.returnType().name();
        MethodDesc methodDesc = Jandex2Gizmo.methodDescOf(method);

        Expr computeFn = bc.lambda(Function.class, lc -> {
            ParamVar ctx = lc.parameter("ctx", 0);
            lc.body(lb -> {
                Expr result = lb.invokeStatic(methodDesc, ctx);
                Expr uni = createUniFromValue(lb, typeName, result);
                lb.return_(uni);
            });
        });
        return bc.invokeInterface(
                MethodDesc.of(Flag.Builder.class, "setComputeAsync", Flag.Builder.class, Function.class),
                builder, computeFn);
    }

    private Expr createUniFromValue(BlockCreator bc, DotName typeName, Expr value) {
        if (isPrimitiveBooleanOrWrapper(typeName)) {
            return bc.invokeStatic(
                    MethodDesc.of(BooleanValue.class, "createUni", Uni.class, boolean.class), value);
        } else if (isPrimitiveIntOrWrapper(typeName)) {
            return bc.invokeStatic(
                    MethodDesc.of(IntValue.class, "createUni", Uni.class, int.class), value);
        } else if (typeName.equals(DotName.createSimple(String.class))) {
            return bc.invokeStatic(
                    MethodDesc.of(StringValue.class, "createUni", Uni.class, String.class), value);
        } else if (typeName.equals(DotName.createSimple(BigDecimal.class))) {
            return bc.invokeStatic(
                    MethodDesc.of(BigDecimalValue.class, "createUni", Uni.class, BigDecimal.class), value);
        } else {
            Expr uniCreate = bc.invokeStatic(MethodDesc.of(Uni.class, "createFrom", UniCreate.class));
            return bc.invokeVirtual(
                    MethodDesc.of(UniCreate.class, "item", Uni.class, Object.class),
                    uniCreate, value);
        }
    }

    // --- Annotation resolution helpers ---

    private String resolveFlagName(AnnotationInstance annotation) {
        AnnotationValue nameValue = annotation.value("name");
        String name = nameValue != null ? nameValue.asString() : RegisterFlag.ELEMENT_NAME;
        String elementName;
        String declaringClassName;

        if (annotation.target().kind() == AnnotationTarget.Kind.FIELD) {
            FieldInfo field = annotation.target().asField();
            elementName = field.name();
            declaringClassName = field.declaringClass().name().toString();
        } else {
            MethodInfo method = annotation.target().asMethod();
            elementName = method.name();
            declaringClassName = method.declaringClass().name().toString();
        }

        if (RegisterFlag.ELEMENT_NAME.equals(name)) {
            return elementName;
        } else if (RegisterFlag.FQCN_ELEMENT_NAME.equals(name)) {
            return declaringClassName + "." + elementName;
        }
        return name;
    }

    private Map<String, String> collectMetadata(AnnotationTarget target) {
        Map<String, String> metadata = new LinkedHashMap<>();
        List<AnnotationInstance> withMetadataList = new ArrayList<>();

        AnnotationInstance listAnnotation = target.declaredAnnotation(WITH_METADATA_LIST);
        if (listAnnotation != null) {
            for (AnnotationInstance nested : listAnnotation.value().asNestedArray()) {
                withMetadataList.add(nested);
            }
        }
        AnnotationInstance singleAnnotation = target.declaredAnnotation(WITH_METADATA);
        if (singleAnnotation != null) {
            withMetadataList.add(singleAnnotation);
        }

        for (AnnotationInstance wm : withMetadataList) {
            metadata.put(wm.value("key").asString(), wm.value("value").asString());
        }

        AnnotationInstance withEvaluator = target.declaredAnnotation(WITH_EVALUATOR);
        if (withEvaluator != null) {
            if (metadata.containsKey(FlagEvaluator.META_KEY)) {
                throw new IllegalStateException(
                        "@WithEvaluator and @WithMetadata(key = \"evaluator\", ...) cannot both be used on the same target");
            }
            metadata.put(FlagEvaluator.META_KEY, withEvaluator.value().asString());
        }
        return metadata;
    }

    // --- Type helpers ---

    private String getDeclaringClassName(RegisteredFlagBuildItem item) {
        if (item.getDeclaration().kind() == AnnotationTarget.Kind.FIELD) {
            return item.getDeclaration().asField().declaringClass().name().toString();
        }
        return item.getDeclaration().asMethod().declaringClass().name().toString();
    }

    private boolean isPrimitiveBooleanOrWrapper(DotName typeName) {
        return typeName.equals(DotName.createSimple(boolean.class))
                || typeName.equals(DotName.createSimple(Boolean.class));
    }

    private boolean isPrimitiveIntOrWrapper(DotName typeName) {
        return typeName.equals(DotName.createSimple(int.class))
                || typeName.equals(DotName.createSimple(Integer.class));
    }

    private String buildMethodDescriptor(MethodInfo method) {
        StringBuilder sb = new StringBuilder("(");
        for (Type paramType : method.parameterTypes()) {
            sb.append(toAsmDescriptor(paramType));
        }
        sb.append(")");
        sb.append(toAsmDescriptor(method.returnType()));
        return sb.toString();
    }

    private String toAsmDescriptor(Type type) {
        return switch (type.kind()) {
            case PRIMITIVE -> switch (type.asPrimitiveType().primitive()) {
                case BOOLEAN -> "Z";
                case INT -> "I";
                case LONG -> "J";
                case BYTE -> "B";
                case SHORT -> "S";
                case CHAR -> "C";
                case FLOAT -> "F";
                case DOUBLE -> "D";
            };
            case VOID -> "V";
            default -> "L" + type.name().toString().replace('.', '/') + ";";
        };
    }

    // --- Bytecode transformation ---

    private record FlagAccessInfo(String flagName, Type type, boolean hasComputationContext) {
        FlagAccessInfo(String flagName, Type type) {
            this(flagName, type, false);
        }
    }

    static class RegisterFlagClassVisitorFunction
            implements BiFunction<String, ClassVisitor, ClassVisitor> {

        private final Map<String, FlagAccessInfo> fieldAccesses;
        private final Map<String, FlagAccessInfo> methodAccesses;

        RegisterFlagClassVisitorFunction(Map<String, FlagAccessInfo> fieldAccesses,
                Map<String, FlagAccessInfo> methodAccesses) {
            this.fieldAccesses = fieldAccesses;
            this.methodAccesses = methodAccesses;
        }

        @Override
        public ClassVisitor apply(String className, ClassVisitor classVisitor) {
            return new RegisterFlagClassVisitor(io.quarkus.gizmo.Gizmo.ASM_API_VERSION, classVisitor,
                    fieldAccesses, methodAccesses);
        }
    }

    static class RegisterFlagClassVisitor extends ClassVisitor {

        private final Map<String, FlagAccessInfo> fieldAccesses;
        private final Map<String, FlagAccessInfo> methodAccesses;

        RegisterFlagClassVisitor(int api, ClassVisitor classVisitor,
                Map<String, FlagAccessInfo> fieldAccesses,
                Map<String, FlagAccessInfo> methodAccesses) {
            super(api, classVisitor);
            this.fieldAccesses = fieldAccesses;
            this.methodAccesses = methodAccesses;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new RegisterFlagMethodVisitor(api, mv, fieldAccesses, methodAccesses);
        }
    }

    static class RegisterFlagMethodVisitor extends MethodVisitor {

        private static final String FLAG_INTERNAL = "io/quarkiverse/flags/Flag";
        private static final String FLAG_DESCRIPTOR = "Lio/quarkiverse/flags/Flag;";

        private final Map<String, FlagAccessInfo> fieldAccesses;
        private final Map<String, FlagAccessInfo> methodAccesses;

        RegisterFlagMethodVisitor(int api, MethodVisitor methodVisitor,
                Map<String, FlagAccessInfo> fieldAccesses,
                Map<String, FlagAccessInfo> methodAccesses) {
            super(api, methodVisitor);
            this.fieldAccesses = fieldAccesses;
            this.methodAccesses = methodAccesses;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.GETSTATIC) {
                FlagAccessInfo info = fieldAccesses.get(owner + "." + name);
                if (info != null) {
                    emitFlagAccess(info.flagName, info.type);
                    return;
                }
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC) {
                FlagAccessInfo info = methodAccesses.get(owner + "." + name + descriptor);
                if (info != null) {
                    if (info.hasComputationContext) {
                        super.visitInsn(Opcodes.POP);
                    }
                    emitFlagAccess(info.flagName, info.type);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        private void emitFlagAccess(String flagName, Type type) {
            super.visitLdcInsn(flagName);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, FLAG_INTERNAL, "get",
                    "(Ljava/lang/String;)" + FLAG_DESCRIPTOR, true);

            DotName typeName = type.name();
            if (typeName.equals(DotName.createSimple(boolean.class))
                    || typeName.equals(DotName.createSimple(Boolean.class))) {
                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, FLAG_INTERNAL, "isEnabled", "()Z", true);
                if (typeName.equals(DotName.createSimple(Boolean.class))) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf",
                            "(Z)Ljava/lang/Boolean;", false);
                }
            } else if (typeName.equals(DotName.createSimple(int.class))
                    || typeName.equals(DotName.createSimple(Integer.class))) {
                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, FLAG_INTERNAL, "getInt", "()I", true);
                if (typeName.equals(DotName.createSimple(Integer.class))) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                            "(I)Ljava/lang/Integer;", false);
                }
            } else if (typeName.equals(DotName.createSimple(String.class))) {
                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, FLAG_INTERNAL, "getString",
                        "()Ljava/lang/String;", true);
            } else if (typeName.equals(DotName.createSimple(BigDecimal.class))) {
                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, FLAG_INTERNAL, "getDecimal",
                        "()Ljava/math/BigDecimal;", true);
            } else if (typeName.equals(FLAG_VALUE)) {
                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, FLAG_INTERNAL, "computeAndAwait",
                        "()Lio/quarkiverse/flags/Flag$Value;", true);
            }
        }
    }
}
