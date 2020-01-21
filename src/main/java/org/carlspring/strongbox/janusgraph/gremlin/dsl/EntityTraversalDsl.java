package org.carlspring.strongbox.janusgraph.gremlin.dsl;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.GremlinDsl;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.carlspring.strongbox.janusgraph.domain.DomainObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GremlinDsl
public interface EntityTraversalDsl<S, E> extends GraphTraversal.Admin<S, E>
{

    Logger logger = LoggerFactory.getLogger(EntityTraversalDsl.class);

    Object NULL = new Object()
    {

        @Override
        public String toString()
        {
            return "__null";
        }

    };

    @SuppressWarnings("unchecked")
    default <E2> GraphTraversal<S, E2> findById(String label,
                                                Object uuid)
    {
        return (GraphTraversal<S, E2>) hasLabel(label).has("uuid", uuid);
    }

    @SuppressWarnings("unchecked")
    default Traversal<S, Object> enrichPropertyValue(String propertyName)
    {
        return coalesce(__.properties(propertyName).value(), __.<Object>constant(NULL));
    }

    @SuppressWarnings("unchecked")
    default Traversal<S, Object> enrichPropertyValues(String propertyName)
    {
        return coalesce(__.propertyMap(propertyName).map(t -> t.get().get(propertyName)), __.<Object>constant(NULL));
    }

    default <S2> Traversal<S, Object> mapToObject(Traversal<S2, Object> enrichObjectTraversal)
    {
        return fold().choose(t -> t.isEmpty(),
                             __.<Object>constant(NULL),
                             __.<Edge>unfold().map(enrichObjectTraversal));
    }

    default <S2> Traversal<S, Vertex> saveV(String label,
                                            Object uuid,
                                            Traversal<S2, Vertex> unfoldTraversal)
    {
        uuid = Optional.ofNullable(uuid)
                       .orElse(NULL);
        GraphTraversal<S, DomainObject> element = findById(label, uuid);

        return element.fold()
                      .choose(t -> t.isEmpty(),
                              __.addV(label)
                                .property("uuid",
                                           Optional.of(uuid)
                                           .filter(x -> !NULL.equals(x))
                                           .orElse(UUID.randomUUID().toString()))
                                .trace("Created"),
                              __.unfold()
                                .trace("Fetched"))
                      .map(unfoldTraversal);
    }

    default <E2> Traversal<S, E2> trace(String action)
    {
        return (Traversal<S, E2>) sideEffect(t -> logger.debug(String.format("%s [%s]-[%s]-[%s]",
                                                                             action,
                                                                             ((Element) t.get()).label(),
                                                                             ((Element) t.get()).id(),
                                                                             ((Element) t.get()).property("uuid")
                                                                                                .value())));
    }

}
