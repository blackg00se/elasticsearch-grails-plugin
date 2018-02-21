package grails.plugins.elasticsearch.mapping

import grails.core.GrailsApplication
import grails.core.GrailsDomainClass
import groovy.transform.CompileStatic
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.hibernate.Hibernate
import org.hibernate.metadata.ClassMetadata
import org.hibernate.proxy.HibernateProxy
import org.springframework.beans.factory.annotation.Autowired
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CompileStatic
class DomainReflectionService {
    
    private static final Logger LOG = LoggerFactory.getLogger(this)

    GrailsApplication grailsApplication

    @Autowired
    MappingContext mappingContext

    private final Map<Class<?>, DomainEntity> entityCache = [:]

    private final Map<Class<?>, DomainEntity> abstractEntityCache = [:]

    boolean isDomainEntity(Class<?> clazz) {
        if(clazz in HibernateProxy) {
            clazz = clazz.superclass
        }
        DomainClassArtefactHandler.isDomainClass(clazz)
    }
    
    DomainEntity getDomainEntity(Object instance) {
        // it's the class - just use it..
        //if (instance instanceof Class<?>) return getDomainEntity(instance)

        DomainEntity rv = getDomainEntity(instance.class)
        if (!rv) {
            try {
                // Hibernate.initialize(instance)
                Class<?> clazz = Hibernate.getClass(instance)
                if (clazz)
                    rv = getDomainEntity(clazz)
            } catch (Exception e) {
                LOG.error("Unable to retrieve proxied class where instance.class.name=${instance.class.name}")
            }
        }
        return rv
    }

    DomainEntity getDomainEntity(Class<?> clazz) {
        if (!isDomainEntity(clazz)) return null

        entityCache.computeIfAbsent(clazz) {
            def artefact = getDomainClassArtefact(clazz)

            PersistentEntity persistentEntity = mappingContext.getPersistentEntity(clazz.canonicalName)

            artefact ? new DomainEntity(this, artefact, persistentEntity) : null
        }
    }

    Collection<DomainEntity> getDomainEntities() {
        grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE).toList()
                         .collect { getDomainEntity(((GrailsDomainClass) it).clazz) }
    }

    DomainEntity getAbstractDomainEntity(Class<?> clazz) {
        verifyDomainClass(clazz)

        abstractEntityCache.computeIfAbsent(clazz) {
            new DomainEntity(this, clazz)
        }
    }

    SearchableDomainClassMapper createDomainClassMapper(DomainEntity entity) {
        def config = grailsApplication?.config?.elasticSearch as ConfigObject
        new SearchableDomainClassMapper(grailsApplication, this, entity, config)
    }

    private GrailsDomainClass getDomainClassArtefact(Class<?> clazz) {
        (GrailsDomainClass) grailsApplication.getArtefact(DomainClassArtefactHandler.TYPE, clazz.canonicalName)
    }

    private void verifyDomainClass(Class<?> clazz) {
        if (!isDomainEntity(clazz)) {
            throw new IllegalStateException("Class ${clazz.canonicalName} is not a domain class")
        }
    }

}
