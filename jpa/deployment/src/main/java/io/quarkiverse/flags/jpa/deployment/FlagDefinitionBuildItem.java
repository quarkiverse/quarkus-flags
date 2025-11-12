package io.quarkiverse.flags.jpa.deployment;

import static org.jboss.jandex.gizmo2.Jandex2Gizmo.fieldDescOf;
import static org.jboss.jandex.gizmo2.Jandex2Gizmo.methodDescOf;

import java.lang.reflect.Modifier;

import jakarta.persistence.Entity;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import io.quarkiverse.flags.jpa.FlagDefinition;
import io.quarkiverse.flags.jpa.FlagFeature;
import io.quarkiverse.flags.jpa.FlagMetadata;
import io.quarkiverse.flags.jpa.FlagValue;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.bean.JavaBeanUtil;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Var;
import io.quarkus.gizmo2.creator.BlockCreator;

/**
 *
 * @see FlagDefinition
 */
final class FlagDefinitionBuildItem extends SimpleBuildItem {

    private final ClassInfo entityClass;

    private final Property feature;

    private final Property value;

    private final Property metadata;

    FlagDefinitionBuildItem(ClassInfo entityClass, boolean isPanache) {
        this.entityClass = entityClass;
        AnnotationInstance flagFeature = entityClass.annotation(FlagFeature.class);
        if (flagFeature == null) {
            throw new IllegalStateException("@FlagFeature not declared on " + entityClass);
        }
        this.feature = flagFeature.target().kind() == Kind.FIELD ? new FieldProperty(flagFeature.target().asField(), isPanache)
                : new GetterProperty(flagFeature.target().asMethod());
        AnnotationInstance flagValue = entityClass.annotation(FlagValue.class);
        if (flagValue == null) {
            throw new IllegalStateException("@FlagState not declared on " + entityClass);
        }
        this.value = flagValue.target().kind() == Kind.FIELD ? new FieldProperty(flagValue.target().asField(), isPanache)
                : new GetterProperty(flagValue.target().asMethod());
        AnnotationInstance flagMetadata = entityClass.annotation(FlagMetadata.class);
        if (flagMetadata == null) {
            this.metadata = null;
        } else {
            this.metadata = flagMetadata.target().kind() == Kind.FIELD
                    ? new FieldProperty(flagMetadata.target().asField(), isPanache)
                    : new GetterProperty(flagMetadata.target().asMethod());
        }
    }

    public String getEntityName() {
        AnnotationInstance entity = entityClass.declaredAnnotation(Entity.class);
        if (entity != null) {
            AnnotationValue nameValue = entity.value("name");
            if (nameValue != null) {
                return nameValue.asString();
            }
        }
        return entityClass.name().withoutPackagePrefix();
    }

    public ClassInfo getEntityClass() {
        return entityClass;
    }

    public Property getFeature() {
        return feature;
    }

    public Property getValue() {
        return value;
    }

    public Property getMetadata() {
        return metadata;
    }

    interface Property {

        AnnotationTarget target();

        Expr read(Var item, BlockCreator bc);

    }

    static class FieldProperty implements Property {

        final FieldInfo field;
        final MethodInfo getter;

        FieldProperty(FieldInfo field, boolean isPanache) {
            this.field = field;
            this.getter = findGetter(field, isPanache);
        }

        @Override
        public AnnotationTarget target() {
            return field;
        }

        @Override
        public Expr read(Var item, BlockCreator bc) {
            if (getter != null) {
                return bc.invokeVirtual(methodDescOf(getter), item);
            }
            return bc.get(item.field(fieldDescOf(field)));
        }

    }

    static class GetterProperty implements Property {

        final MethodInfo getter;

        private GetterProperty(MethodInfo getter) {
            this.getter = getter;
        }

        @Override
        public AnnotationTarget target() {
            return getter;
        }

        @Override
        public Expr read(Var item, BlockCreator bc) {
            return bc.invokeVirtual(methodDescOf(getter), item);
        }

    }

    private static MethodInfo findGetter(FieldInfo field, boolean isPanache) {
        String getterName = "get" + JavaBeanUtil.capitalize(field.name());
        if (isPanache) {
            // Panache adds getters directly to the bytecode
            return MethodInfo.create(field.declaringClass(), getterName, new Type[0], field.type(), (short) Modifier.PUBLIC);
        }
        return field.declaringClass()
                .methods()
                .stream()
                .filter(m -> m.name().equals(getterName))
                .findFirst()
                .orElse(null);
    }

}
