package org.opencypher.gremlin.neo4j.ogm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CypherQueryUtils
{

    private static final String MATCH = "MATCH";
    private static final String WHERE = "WHERE";
    private static final String WITH = "WITH";
    private static final String RETURN = "RETURN";
    private static final Logger logger = LoggerFactory.getLogger(CypherQueryUtils.class);

    public static String normalizeMatchByIdWithRelationResult(String cypherStatement, String id)
    {
        // MATCH (n:`RepositoryArtifactIdGroup`)
        // WHERE n.`uuid` = { id }
        // WITH n
        // RETURN n,
        // _______[
        // _________[
        // ___________(n)-[r_r1:`RepositoryArtifactIdGroupEntity_ArtifactGroupEntity`]->(a1:`ArtifactGroup`)
        // ___________| [ r_r1, a1 ]
        // _________]
        // _______]
        if (!cypherStatement.startsWith(MATCH))
        {
            return cypherStatement;
        }

        int whereIndex = cypherStatement.indexOf(WHERE);
        int withIndex = cypherStatement.indexOf(WITH);
        int returnIndex = cypherStatement.indexOf(RETURN);

        if (whereIndex < 0 || withIndex < 0 || returnIndex < 0)
        {
            return cypherStatement;
        }
        
        if (whereIndex >= withIndex || withIndex >= returnIndex)
        {
            return cypherStatement;
        }

        String matchClause = cypherStatement.substring(0, whereIndex);
        String whereClause = cypherStatement.substring(whereIndex, withIndex);
        String withClause = cypherStatement.substring(withIndex, returnIndex);
        String returnClause = cypherStatement.substring(returnIndex);

        logger.trace(String.format("Cyphter With: %s", withClause));
        logger.trace(String.format("Cyphter Return: %s", returnClause));

        String returnResults = returnClause.replace(RETURN, "");
        List<String> returnTokens = new ArrayList<>();
        for (int i = 0, j = returnResults.indexOf(","); j > 0; i = j + 1, j = returnResults.indexOf(",", i))
        {
            String returnToken = returnResults.substring(i, j).trim();
            if (returnToken.startsWith("["))
            {
                // this is relations query result token, which is commonly the
                // last
                returnTokens.add(returnResults.substring(i).trim());
                break;
            }
            else
            {
                // this is just a regular return token like some alias
                returnTokens.add(returnToken);
            }
        }

        //return relations subquery should be last element
        String returnRelationsClause = returnTokens.get(returnTokens.size() - 1);
        returnTokens.remove(returnTokens.size() - 1);
        if (!returnRelationsClause.startsWith("[") && !returnRelationsClause.endsWith("]"))
        {
            logger.trace("Return clause without relations.");

            return cypherStatement;
        }

        // Remove list outer brackets
        returnRelationsClause = returnRelationsClause.substring(1, returnRelationsClause.length() - 1).trim();
        // Remove frist bracket for first element and last bracket for last
        // element
        returnRelationsClause = returnRelationsClause.substring(1, returnRelationsClause.length() - 1).trim();

        Map<String, String> withRelations = new HashMap<>();
        for (String returnRelationClause : returnRelationsClause.split("\\]\\s*,\\s*\\["))
        {
            if (!returnRelationClause.contains("|"))
            {
                throw new RuntimeException("Relation pattern not match.");
            }

            String[] returnRelationTokens = returnRelationClause.split("\\|");
            if (returnRelationTokens.length != 2)
            {
                throw new RuntimeException("Return relation pattern not match.");
            }

            String relationQueryResult = returnRelationTokens[1].trim();
            // remove brackets
            relationQueryResult = relationQueryResult.substring(1, relationQueryResult.length() - 1).trim();
            // map relation results into relation query 
            withRelations.put(relationQueryResult.trim(), returnRelationTokens[0].trim());
        }

        withClause = withRelations.entrySet()
                                  .stream()
                                  .map(e -> {
                                      String relationQueryResult = e.getKey();
                                      Arrays.stream(relationQueryResult.split(",")).forEach(r -> returnTokens.add(r));
                                      String localWith = returnTokens.stream()
                                                                     .reduce((r1,
                                                                              r2) -> r1.trim() + ", " + r2.trim())
                                                                     .get();
                                      return MATCH + " " + e.getValue() + " " + WITH + " " + localWith;
                                  })
                                  .reduce((w1,
                                           w2) -> w1 + " " + w2)
                                  .get();

        returnClause = RETURN + " " + returnTokens.stream()
                                                  .reduce((r1,
                                                           r2) -> r1.trim() + ", " + r2.trim())
                                                  .get();
        
        cypherStatement = matchClause + " " + whereClause + " " + WITH + " n " + withClause + " " + returnClause;
        cypherStatement = cypherStatement.replace("{ id }", "'" + id + "'");
        return cypherStatement;
    }

    public static void main(String[] args)
    {
        String query = "MATCH (n:`RepositoryArtifactIdGroup`) " +
                "WHERE n.`uuid` = { id } " +
                "WITH n " +
                "RETURN n, " +
                "[ [ (n)-[r_r1:`RepositoryArtifactIdGroupEntity_ArtifactGroupEntity`]->(a1:`ArtifactGroup`) | [ r_r1, a1 ] ], " +
                "  [ (n)-[r_r2:`RepositoryArtifactIdGroupEntity_ArtifactGroupEntity`]->(a2:`ArtifactGroup`) | [ r_r2, a2 ] ] ]";
        
        //String query = "MATCH (n:`RepositoryArtifactIdGroup`) WHERE n.`uuid` = { id } WITH n RETURN n,[ [ (n)-[r_r1:`RepositoryArtifactIdGroupEntity_ArtifactGroupEntity`]->(a1:`ArtifactGroup`) | [ r_r1, a1 ] ] ]";
        
        System.out.println(normalizeMatchByIdWithRelationResult(query, "123"));
    }

}
