package org.carlspring.strongbox.janusgraph.reposiotries;

import static org.apache.tinkerpop.gremlin.process.traversal.P.eq;
import static org.apache.tinkerpop.gremlin.process.traversal.P.neq;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.carlspring.strongbox.janusgraph.app.Application;
import org.carlspring.strongbox.janusgraph.domain.Artifact;
import org.carlspring.strongbox.janusgraph.domain.ArtifactCoordinates;
import org.carlspring.strongbox.janusgraph.domain.ArtifactCoordinatesEntity;
import org.carlspring.strongbox.janusgraph.domain.ArtifactDependency;
import org.carlspring.strongbox.janusgraph.domain.ArtifactDependencyEntity;
import org.carlspring.strongbox.janusgraph.domain.ArtifactEntity;
import org.carlspring.strongbox.janusgraph.domain.Edges;
import org.carlspring.strongbox.janusgraph.repositories.ArtifactCoordinatesRepository;
import org.carlspring.strongbox.janusgraph.repositories.ArtifactEntryRepository;
import org.janusgraph.core.JanusGraph;
import org.junit.jupiter.api.Test;
import org.neo4j.ogm.session.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = Application.class)
public class ArtifactEntryRepositoryTest
{

    private static final Logger logger = LoggerFactory.getLogger(ArtifactEntryRepositoryTest.class);

    @Inject
    private ArtifactEntryRepository artifactEntryRepository;

    @Inject
    private ArtifactCoordinatesRepository artifactCoordinatesRepository;

    @Inject
    private JanusGraph janusGraph;

    @Inject
    private SessionFactory sessionFactory;

    @Test
    public void crudShouldWork()
    {
        ArtifactCoordinatesEntity artifactCoordinates = new ArtifactCoordinatesEntity();
        artifactCoordinates.setPath("org/carlspring/test-artifact-3.0.0.jar");
        artifactCoordinates.setUuid(UUID.randomUUID().toString());
        artifactCoordinates.setVersion("3.0.0");

        Date createdOn = new Date();
        ArtifactEntity artifactEntry = new ArtifactEntity();
        artifactEntry.setUuid(UUID.randomUUID().toString());
        artifactEntry.setStorageId("storage0");
        artifactEntry.setRepositoryId("releases");
        artifactEntry.setSizeInBytes(123L);
        artifactEntry.setCreated(createdOn);
        artifactEntry.setTags(new HashSet<>(Arrays.asList("release", "stabile")));
        artifactEntry.setArtifactCoordinates(artifactCoordinates);

        ArtifactEntity artifactEntrySaved = artifactEntryRepository.save(artifactEntry);
        assertEquals(artifactEntrySaved.getUuid(), artifactEntry.getUuid());

        ArtifactDependencyEntity artifactDependency = new ArtifactDependencyEntity();
        artifactDependency.setUuid(UUID.randomUUID().toString());
        artifactDependency.setSubject(artifactEntry);
        artifactDependency.setDependency(artifactCoordinates);
        sessionFactory.openSession().save(artifactDependency);

        artifactEntrySaved = artifactEntryRepository.findByPath("org/carlspring/test-artifact-3.0.0.jar");
        assertNotNull(artifactEntrySaved);
        assertEquals(artifactEntrySaved.getUuid(), artifactEntry.getUuid());
        assertNotNull(artifactEntrySaved.getArtifactCoordinates());
        assertEquals(createdOn, artifactEntrySaved.getCreated());
        Set<String> tags = artifactEntrySaved.getTags();
        assertNotNull(tags);
        assertEquals(2, tags.size());
        assertTrue(tags.contains("release"));
        assertTrue(tags.contains("stabile"));
    }

    @Test
    public void manyToOneRelationShouldWork()
    {
        String artifactEntryUuid = UUID.randomUUID().toString();
        String artifactCoordinatesUuid = UUID.randomUUID().toString();

        GraphTraversalSource g = janusGraph.traversal();
        g.addV(ArtifactCoordinates.LABEL)
         .property("uuid", artifactCoordinatesUuid)
         .property("path",
                   "org/carlspring/test-artifact-4.0.0.jar")
         .property("version",
                   "4.0.0")
         .as("ac")
         .addV(Artifact.LABEL)
         .property("uuid", artifactEntryUuid)
         .property("storageId", "storage0")
         .property("repositoryId", "releases")
         .property("sizeInBytes", 123L)
         .property("tags", new HashSet<>(Arrays.asList("release", "stabile")))
         .property("created", new Date())
         .as("ae")
         .addE(Edges.ARTIFACT_ARTIFACTCOORDINATES)
         .to("ac")
         .next();

        // Relation should exists
        GraphTraversal<Vertex, Edge> edgeQuery = g.V()
                                                  .hasLabel(Artifact.LABEL)
                                                  .has("uuid", artifactEntryUuid)
                                                  .outE();
        assertTrue(edgeQuery.hasNext());
        Edge artifactEntry2ArtifactCoordinatesEdge = edgeQuery.next();
        logger.info(String.valueOf(artifactEntry2ArtifactCoordinatesEdge));
        assertEquals(Edges.ARTIFACT_ARTIFACTCOORDINATES,
                     artifactEntry2ArtifactCoordinatesEdge.label());

        // ArtifactCoordinates should exists
        GraphTraversal<Vertex, Vertex> vertexQuery = g.V()
                                                      .hasLabel(Artifact.LABEL)
                                                      .has("uuid", artifactEntryUuid)
                                                      .outE()
                                                      .inV();
        assertTrue(vertexQuery.hasNext());
        Vertex artifactCoordinatesVertex = vertexQuery.next();
        logger.info(String.valueOf(artifactCoordinatesVertex));
        assertEquals(artifactCoordinatesUuid, artifactCoordinatesVertex.property("uuid").value());
        assertEquals(ArtifactCoordinates.LABEL, artifactCoordinatesVertex.label());

        // ArtifactCoordinates should still exists
        vertexQuery = g.V()
                       .hasLabel(ArtifactCoordinates.LABEL)
                       .has("path", eq("org/carlspring/test-artifact-4.0.0.jar"))
                       .inE()
                       .outV()
                       .hasLabel(Artifact.LABEL);
        assertTrue(vertexQuery.hasNext());
        Vertex artifactEntryAnotherVertex = vertexQuery.next();
        assertEquals(artifactCoordinatesUuid, artifactCoordinatesVertex.property("uuid").value());
        assertEquals(Artifact.LABEL, artifactEntryAnotherVertex.label());
        
        g.tx().rollback();
    }

    @Test
    public void artifactDependencyTreeShouldWork()
    {
        GraphTraversalSource g = janusGraph.traversal();
        
        // ArtifactEntry->ArtifactDependency\
        // ..................................->ArtifactCoordinates
        // ArtifactEntry->ArtifactDependency/
        // .............\
        // ..............>ArtifactDependency->ArtifactCoordinates
        g.addV(ArtifactCoordinates.LABEL)
         .property("uuid", UUID.randomUUID().toString())
         .property("path",
                   "org/carlspring/test/dependency.jar")
         .property("version",
                   "777")
         .as("adc1")
         .addV(Artifact.LABEL)
         .property("uuid", UUID.randomUUID().toString())
         .property("storageId", "storage0")
         .property("repositoryId", "releases")
         .as("ae1")
         .addE(ArtifactDependency.LABEL)
         .to("adc1")
         .addV(Artifact.LABEL)
         .property("uuid", UUID.randomUUID().toString())
         .property("storageId", "storage0")
         .property("repositoryId", "releases")
         .as("ae2")
         .addE(ArtifactDependency.LABEL)
         .to("adc1")
         .addV(ArtifactCoordinates.LABEL)
         .property("uuid", UUID.randomUUID().toString())
         .property("path",
                   "org/carlspring/test/another-dependency.jar")
         .property("version",
                   "1.2.3")
         .as("adc2")
         .addE(ArtifactDependency.LABEL)
         .from("ae2")
         .to("adc2")
         .next();

        g.tx().commit();

        ArtifactCoordinatesEntity adc1 = artifactCoordinatesRepository.findByPath("org/carlspring/test/dependency.jar");
        List<ArtifactEntity> dependencies = artifactEntryRepository.findAllDependentArtifactEntries(adc1.getUuid());
        assertEquals(2, dependencies.size());
    }

}
