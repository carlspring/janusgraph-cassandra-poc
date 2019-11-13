package org.carlspring.strongbox.janusgraph.graph.schema;

import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.carlspring.strongbox.janusgraph.domain.ArtifactCoordinates;
import org.carlspring.strongbox.janusgraph.domain.ArtifactEntry;
import org.janusgraph.core.Cardinality;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.Multiplicity;
import org.janusgraph.core.PropertyKey;
import org.janusgraph.core.VertexLabel;
import org.janusgraph.core.schema.EdgeLabelMaker;
import org.janusgraph.core.schema.JanusGraphIndex;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.janusgraph.core.schema.JanusGraphSchemaType;
import org.janusgraph.core.schema.PropertyKeyMaker;
import org.janusgraph.graphdb.database.management.ManagementSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StrongboxSchema
{

    private static final Logger logger = LoggerFactory.getLogger(StrongboxSchema.class);

    @Inject
    public void createSchema(JanusGraph jg) throws InterruptedException
    {
        JanusGraphManagement jgm = jg.openManagement();
        try
        {
            applySchemaChanges(jgm);
            logger.info(String.format("Schema: %n%s", jgm.printSchema()));
            jgm.commit();
        }
        catch (Exception e)
        {
            logger.error("Failed to apply schema changes.", e);
            jgm.rollback();
            throw new RuntimeException("Failed to apply schema changes.", e);
        }
        
        jgm = jg.openManagement();
        Set<String> indexes;
        try
        {
            indexes = createIndexes(jg, jgm);
            jgm.commit();
        }
        catch (Exception e)
        {
            logger.error("Failed to create indexes.", e);
            jgm.rollback();
            throw new RuntimeException("Failed to create indexes.", e);
        }
        
        for (String janusGraphIndex : indexes)
        {
            ManagementSystem.awaitGraphIndexStatus(jg, janusGraphIndex).call();
        }
    }

    protected Set<String> createIndexes(JanusGraph jg, JanusGraphManagement jgm) throws InterruptedException
    {
        Set<String> result = new HashSet<>();

        PropertyKey propertyPath = jgm.getPropertyKey("path");
        VertexLabel vertexLabel = jgm.getVertexLabel(ArtifactCoordinates.class.getSimpleName());

        buildIndexIfNecessary(jgm, ArtifactCoordinates.class.getSimpleName() + ".path", Vertex.class, propertyPath, vertexLabel)
                .ifPresent(result::add);

        return result;
    }

    private void applySchemaChanges(JanusGraphManagement jgm)
    {
        // Properties
        makePropertyKeyIfDoesNotExist(jgm, "uuid", String.class);
        makePropertyKeyIfDoesNotExist(jgm, "storageId", String.class);
        makePropertyKeyIfDoesNotExist(jgm, "repositoryId", String.class);
        makePropertyKeyIfDoesNotExist(jgm, "sizeInBytes", Long.class);
        makePropertyKeyIfDoesNotExist(jgm, "created", Date.class);
        makePropertyKeyIfDoesNotExist(jgm, "tags", String.class, Cardinality.SET);

        makePropertyKeyIfDoesNotExist(jgm, "path", String.class);
        makePropertyKeyIfDoesNotExist(jgm, "version", String.class);

        // Vertices
        makeVertexLabelIfDoesNotExist(jgm, ArtifactEntry.class.getSimpleName());
        makeVertexLabelIfDoesNotExist(jgm, ArtifactCoordinates.class.getSimpleName());

        // Edges
        makeEdgeLabelIfDoesNotExist(jgm, ArtifactEntry.class.getSimpleName() + "#" +
                                         ArtifactCoordinates.class.getSimpleName(), Multiplicity.MANY2ONE);
    }

    private Optional<String> buildIndexIfNecessary(final JanusGraphManagement jgm,
                                                   final String name,
                                                   final Class<? extends Element> elementType,
                                                   final PropertyKey propertyPath,
                                                   final JanusGraphSchemaType schemaType)
    {
        if (jgm.containsGraphIndex(name))
        {
            return Optional.empty();
        }

        JanusGraphManagement.IndexBuilder indexBuilder = jgm.buildIndex(name, elementType);
        if (propertyPath != null)
        {
            indexBuilder = indexBuilder.addKey(propertyPath);
        }
        if (schemaType != null)
        {
            indexBuilder = indexBuilder.indexOnly(schemaType);
        }

        JanusGraphIndex janusGraphIndex = indexBuilder.buildCompositeIndex();
        return Optional.of(janusGraphIndex.name());
    }

    private void makeEdgeLabelIfDoesNotExist(final JanusGraphManagement jgm,
                                             final String name,
                                             final Multiplicity multiplicity)
    {
        if (jgm.containsEdgeLabel(name))
        {
            return;
        }
        EdgeLabelMaker edgeLabelMaker = jgm.makeEdgeLabel(name);
        if (multiplicity != null)
        {
            edgeLabelMaker = edgeLabelMaker.multiplicity(Multiplicity.MANY2ONE);
        }

        edgeLabelMaker.make();
    }

    private void makeVertexLabelIfDoesNotExist(final JanusGraphManagement jgm,
                                               final String name)
    {
        if (jgm.containsVertexLabel(name))
        {
            return;
        }
        jgm.makeVertexLabel(name).make();
    }

    private void makePropertyKeyIfDoesNotExist(final JanusGraphManagement jgm,
                                               final String name,
                                               final Class<?> dataType)
    {
        makePropertyKeyIfDoesNotExist(jgm, name, dataType, null);
    }

    private void makePropertyKeyIfDoesNotExist(final JanusGraphManagement jgm,
                                               final String name,
                                               final Class<?> dataType,
                                               final Cardinality cardinality)
    {
        if (jgm.containsPropertyKey(name))
        {
            return;
        }

        PropertyKeyMaker propertyKeyMaker = jgm.makePropertyKey(name).dataType(dataType);
        if (cardinality != null)
        {
            propertyKeyMaker = propertyKeyMaker.cardinality(cardinality);
        }
        propertyKeyMaker.make();

    }

}
