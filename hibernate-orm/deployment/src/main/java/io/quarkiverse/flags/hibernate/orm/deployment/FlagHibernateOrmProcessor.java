package io.quarkiverse.flags.hibernate.orm.deployment;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import org.jboss.jandex.ClassInfo;
import org.jboss.logging.Logger;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.InMemoryFlagProvider;
import io.quarkiverse.flags.hibernate.orm.deployment.FlagSourceBuildItem.Property;
import io.quarkiverse.flags.hibernate.orm.runtime.AbstractHibernateOrmFlagProvider;
import io.quarkiverse.flags.runtime.impl.ConfigFlagProvider;
import io.quarkiverse.flags.spi.ComponentOrder;
import io.quarkiverse.flags.spi.FlagManager;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmo2Adaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ConstantBootstrapBuildItem;
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
import io.smallrye.common.annotation.Identifier;

public class FlagHibernateOrmProcessor {

    private static final Logger LOG = Logger.getLogger(FlagHibernateOrmProcessor.class);

    @BuildStep
    void generateFlagProvider(FlagHibernateOrmBuildTimeConfig config, List<PersistenceUnitDescriptorBuildItem> descriptors,
            List<FlagSourceBuildItem> flagSources, BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<ConstantBootstrapBuildItem> constantBootstraps) {
        if (flagSources.isEmpty()) {
            LOG.debugf("No @FlagSource found - no JPA FlagProvider will be generated");
            return;
        }
        if (descriptors.stream().noneMatch(pud -> pud.getPersistenceUnitName().equals(config.persistenceUnitName()))) {
            throw new IllegalStateException("Invalid persistence unit selected: " + config.persistenceUnitName());
        }
        ClassOutput classOutput = new GeneratedBeanGizmo2Adaptor(generatedBeans, constantBootstraps);
        Gizmo gizmo = Gizmo.create(classOutput);

        for (FlagSourceBuildItem flagSource : flagSources) {
            ClassInfo entityClass = flagSource.getEntityClass();
            String className = entityClass.name() + "_HibernateOrmFlagProvider";
            gizmo.class_(className, cc -> {
                This this_ = cc.this_();
                cc.addAnnotation(Singleton.class);
                cc.addAnnotation(Identifier.Literal.of(flagSource.getEntityName()));
                cc.addAnnotation(ComponentOrder.class, ac -> {
                    ac.addArray("before", new String[] { ConfigFlagProvider.ID });
                    ac.addArray("after", new String[] { InMemoryFlagProvider.ID });
                });
                cc.extends_(AbstractHibernateOrmFlagProvider.class);

                // private final EntityManager em;
                FieldDesc emField = cc.field("em", fc -> {
                    fc.setType(EntityManager.class);
                    fc.private_();
                    fc.final_();
                });

                cc.constructor(constructor -> {
                    // MyFlag_HibernateOrmFlagProvider(EntityManager em, FlagManager fm) {
                    //    super(fm, "com.acme.MyFlag_HibernateOrmFlagProvider");
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
                        bc.invokeSpecial(
                                ConstructorDesc.of(AbstractHibernateOrmFlagProvider.class, FlagManager.class, String.class),
                                this_,
                                manager, Const.of(className));
                        bc.set(this_.field(emField), em);
                        bc.return_();
                    });
                });

                cc.method("doGetFlags", mc -> {
                    mc.returning(Collection.class);
                    mc.addAnnotation(Transactional.class);

                    mc.body(bc -> {
                        // List<MyFlag> flags = em.createQuery("from MyFlag", MyFlag.class).getResultList();
                        Expr query = bc.invokeInterface(
                                MethodDesc.of(EntityManager.class, "createQuery", TypedQuery.class, String.class,
                                        Class.class),
                                this_.field(emField),
                                Const.of("from " + flagSource.getEntityName()),
                                Const.of(ClassDesc.of(entityClass.name().toString())));
                        LocalVar flags = bc.localVar("flags",
                                bc.invokeInterface(MethodDesc.of(Query.class, "getResultList", List.class),
                                        query));
                        // List<Flag> ret = new ArrayList(all.size());
                        LocalVar ret = bc.localVar("ret", bc.new_(ArrayList.class, bc.withList(flags).size()));
                        // for (MyFlag myFlag : all) {
                        //    ret.add(this.createFlag(myFlag.feature, myFlag.metadata, myFlag.value));
                        // }
                        bc.forEach(flags, (ibc, item) -> {
                            Expr feature = flagSource.getFeature().read(item, ibc);
                            Expr value = flagSource.getValue().read(item, ibc);
                            Expr metadata;
                            Property metadataProperty = flagSource.getMetadata();
                            if (metadataProperty != null) {
                                metadata = metadataProperty.read(item, ibc);
                            } else {
                                metadata = ibc.mapOf();
                            }
                            ibc.withList(ret)
                                    .add(ibc.invokeVirtual(
                                            MethodDesc.of(AbstractHibernateOrmFlagProvider.class, "createFlag", Flag.class,
                                                    String.class,
                                                    String.class,
                                                    Map.class),
                                            this_, feature, value, metadata));
                        });
                        bc.return_(ret);
                    });
                });

                cc.method("doGetFlag", mc -> {
                    mc.returning(Flag.class);
                    mc.addAnnotation(Transactional.class);

                    ParamVar featureParam = mc.parameter("feature", String.class);

                    mc.body(bc -> {
                        // TypedQuery<MyFlag> query = em.createQuery("from MyFlag where feature = :feature", MyFlag.class);
                        LocalVar query = bc.localVar("query", bc.invokeInterface(
                                MethodDesc.of(EntityManager.class, "createQuery", TypedQuery.class, String.class,
                                        Class.class),
                                this_.field(emField),
                                Const.of("from " + flagSource.getEntityName() + " where "
                                        + flagSource.getFeature().name() + " = :feature"),
                                Const.of(ClassDesc.of(entityClass.name().toString()))));
                        // query.setParameter("feature", "foo");
                        bc.invokeInterface(MethodDesc.of(Query.class, "setParameter", Query.class, String.class, Object.class),
                                query, Const.of(flagSource.getFeature().name()), featureParam);
                        // List<MyFlag> resultList = query.getResultList();
                        LocalVar resultList = bc.localVar("resultList",
                                bc.invokeInterface(MethodDesc.of(Query.class, "getResultList", List.class),
                                        query));
                        LocalVar entity = bc.localVar("entity", bc.invokeVirtual(
                                MethodDesc.of(AbstractHibernateOrmFlagProvider.class, "ensureSingle", Object.class,
                                        List.class, String.class),
                                this_, resultList, featureParam));
                        LocalVar ret = bc.localVar("ret", Const.ofNull(Flag.class));
                        bc.if_(bc.isNotNull(entity), entityNotNull -> {
                            Expr feature = flagSource.getFeature().read(entity, entityNotNull);
                            Expr value = flagSource.getValue().read(entity, entityNotNull);
                            Expr metadata;
                            Property metadataProperty = flagSource.getMetadata();
                            if (metadataProperty != null) {
                                metadata = metadataProperty.read(entity, entityNotNull);
                            } else {
                                metadata = entityNotNull.mapOf();
                            }
                            entityNotNull.set(ret, entityNotNull.invokeVirtual(
                                    MethodDesc.of(AbstractHibernateOrmFlagProvider.class, "createFlag", Flag.class,
                                            String.class,
                                            String.class,
                                            Map.class),
                                    this_, feature, value, metadata));
                            ;
                        });
                        bc.return_(ret);
                    });
                });
            });
        }
    }

}
