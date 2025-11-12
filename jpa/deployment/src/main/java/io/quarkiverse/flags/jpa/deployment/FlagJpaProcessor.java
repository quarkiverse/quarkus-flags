package io.quarkiverse.flags.jpa.deployment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.jpa.FlagDefinition;
import io.quarkiverse.flags.jpa.deployment.FlagDefinitionBuildItem.Property;
import io.quarkiverse.flags.jpa.runtime.AbstractJpaFlagProvider;
import io.quarkiverse.flags.spi.FlagManager;
import io.quarkiverse.flags.spi.FlagProvider;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmo2Adaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationIndexBuildItem;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.This;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.hibernate.orm.deployment.PersistenceUnitDescriptorBuildItem;
import io.quarkus.hibernate.orm.runtime.PersistenceUnitUtil;
import io.quarkus.panache.common.deployment.PanacheEntityClassesBuildItem;

public class FlagJpaProcessor {

    private static final Logger LOG = Logger.getLogger(FlagJpaProcessor.class);

    @BuildStep
    void flagDefinition(ApplicationIndexBuildItem index, List<PanacheEntityClassesBuildItem> panacheEntityClasses,
            BuildProducer<FlagDefinitionBuildItem> flagDefinition) {
        List<AnnotationInstance> flagDefinitions = index.getIndex().getAnnotations(DotName.createSimple(FlagDefinition.class));
        if (flagDefinitions.size() > 1) {
            throw new RuntimeException("At most one entity class can be annotated with @FlagDefinition");
        }
        if (flagDefinitions.isEmpty()) {
            return;
        }
        AnnotationInstance flagDefinitionAnnotation = flagDefinitions.get(0);
        Set<String> panacheEntities = new HashSet<>();
        for (PanacheEntityClassesBuildItem entityClasses : panacheEntityClasses) {
            panacheEntities.addAll(entityClasses.getEntityClasses());
        }
        ClassInfo entityClass = flagDefinitionAnnotation.target().asClass();
        flagDefinition.produce(
                new FlagDefinitionBuildItem(entityClass, panacheEntities.contains(entityClass.name().toString())));
    }

    @BuildStep
    void generateFlagProvider(FlagJpaBuildTimeConfig config, List<PersistenceUnitDescriptorBuildItem> descriptors,
            Optional<FlagDefinitionBuildItem> flagDefinition, BuildProducer<GeneratedBeanBuildItem> generatedBeans) {
        if (flagDefinition.isEmpty()) {
            LOG.debugf("No @FlagDefinition found - JPA FlagProvider will not be generated");
            return;
        }
        if (descriptors.stream().noneMatch(pud -> pud.getPersistenceUnitName().equals(config.persistenceUnitName()))) {
            throw new IllegalStateException("Invalid persistence unit selected: " + config.persistenceUnitName());
        }
        ClassOutput classOutput = new GeneratedBeanGizmo2Adaptor(generatedBeans);
        Gizmo gizmo = Gizmo.create(classOutput);

        ClassInfo entityClass = flagDefinition.get().getEntityClass();

        gizmo.class_(entityClass.name() + "_JpaFlagProvider", cc -> {
            This this_ = cc.this_();
            cc.addAnnotation(Singleton.class);
            cc.extends_(AbstractJpaFlagProvider.class);

            // private final EntityManager em;
            FieldDesc emField = cc.field("em", fc -> {
                fc.setType(EntityManager.class);
                fc.private_();
                fc.final_();
            });

            cc.constructor(constructor -> {
                // MyFlag_JpaFlagProvider(EntityManager em, FlagManager fm) {
                //    super(fm);
                //    this.em = em;
                // }
                constructor.public_();
                ParamVar em = constructor.parameter("em", pc -> {
                    pc.setType(EntityManager.class);
                    if (!config.persistenceUnitName().equals(PersistenceUnitUtil.DEFAULT_PERSISTENCE_UNIT_NAME)) {
                        // Non-default persistence unit used
                        pc.addAnnotation(new PersistenceUnit.PersistenceUnitLiteral(config.persistenceUnitName()));
                    }
                });
                ParamVar manager = constructor.parameter("fm", FlagManager.class);
                constructor.body(bc -> {
                    bc.invokeSpecial(ConstructorDesc.of(AbstractJpaFlagProvider.class, FlagManager.class), this_, manager);
                    bc.set(this_.field(emField), em);
                    bc.return_();
                });
            });

            cc.method("getPriority", mc -> {
                mc.returning(int.class);
                mc.body(bc -> {
                    bc.return_(FlagProvider.DEFAULT_PRIORITY + 5);
                });
            });

            cc.method("getFlags", mc -> {
                mc.returning(Iterable.class);
                mc.addAnnotation(Transactional.class);
                mc.body(bc -> {
                    // List<MyFlag> flags = em.createQuery("from MyFlag").getResultList();
                    Expr query = bc.invokeInterface(
                            MethodDesc.of(EntityManager.class, "createQuery", Query.class, String.class),
                            this_.field(emField),
                            Const.of("from " + flagDefinition.get().getEntityName()));
                    LocalVar flags = bc.localVar("flags",
                            bc.invokeInterface(MethodDesc.of(Query.class, "getResultList", List.class),
                                    query));
                    // List<Flag> ret = new ArrayList(all.size());
                    LocalVar ret = bc.localVar("ret", bc.new_(ArrayList.class, bc.withList(flags).size()));
                    // for (MyFlag myFlag : all) {
                    //    ret.add(this.createFlag(myFlag.feature, myFlag.metadata, myFlag.value));
                    // }
                    bc.forEach(flags, (ibc, item) -> {
                        Expr feature = flagDefinition.get().getFeature().read(item, ibc);
                        Expr value = flagDefinition.get().getValue().read(item, ibc);
                        Expr metadata;
                        Property metadataProperty = flagDefinition.get().getMetadata();
                        if (metadataProperty != null) {
                            metadata = metadataProperty.read(item, ibc);
                        } else {
                            metadata = Const.ofNull(Map.class);
                        }
                        ibc.withList(ret)
                                .add(ibc.invokeVirtual(
                                        MethodDesc.of(AbstractJpaFlagProvider.class, "createFlag", Flag.class, String.class,
                                                String.class,
                                                Map.class),
                                        this_, feature, value, metadata));
                    });
                    bc.return_(ret);
                });
            });

        });

    }

}
