/**
 * GDMS-Topology is a library dedicated to graph analysis. It is based on the
 * JGraphT library available at <http://www.jgrapht.org/>. It enables computing
 * and processing large graphs using spatial and alphanumeric indexes.
 *
 * This version is developed at French IRSTV institut as part of the EvalPDU
 * project, funded by the French Agence Nationale de la Recherche (ANR) under
 * contract ANR-08-VILL-0005-01 and GEBD project funded by the French Ministery
 * of Ecology and Sustainable Development.
 *
 * GDMS-Topology is distributed under GPL 3 license. It is produced by the
 * "Atelier SIG" team of the IRSTV Institute <http://www.irstv.fr/> CNRS FR
 * 2488.
 *
 * Copyright (C) 2009-2012 IRSTV (FR CNRS 2488)
 *
 * GDMS-Topology is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * GDMS-Topology is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GDMS-Topology. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://wwwc.orbisgis.org/> or contact
 * directly: info_at_ orbisgis.org
 */
package org.gdms.gdmstopology.model;

import com.vividsolutions.jts.geom.Geometry;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.apache.log4j.Logger;
import org.gdms.data.DataSourceFactory;
import org.gdms.data.NoSuchTableException;
import org.gdms.data.indexes.DefaultAlphaQuery;
import org.gdms.data.indexes.IndexException;
import org.gdms.data.schema.Metadata;
import org.gdms.data.schema.MetadataUtilities;
import org.gdms.data.values.Value;
import org.gdms.data.values.ValueFactory;
import org.gdms.driver.DataSet;
import org.gdms.driver.DriverException;
import org.jgrapht.EdgeFactory;
import org.jgrapht.graph.AbstractGraph;
import org.jgrapht.graph.ClassBasedEdgeFactory;
import org.orbisgis.progress.ProgressMonitor;

/**
 * <p> Creates a (JGraphT) graph from an existing {@link DataSet}. 
 * 
 * <b>Warning</b>: The input {@link DataSet} must have the 
 * following fields: 
 * <UL> 
 * <LI> start_node (int) </LI> 
 * <LI> end_node (int) </LI> 
 * <LI> weight (double) </LI> 
 * </UL>
 *
 * <p>Documentation added by Adam Gouge.</p>
 *
 * @author Erwan Bocher
 */
public final class GDMSGraph extends AbstractGraph<Integer, GraphEdge> implements GDMSValueGraph<Integer, GraphEdge> {

    // PARAMETERS
    /**
     * An existing data source, often contained in a shapefile (.shp).
     */
    private final DataSet dataSet;
    /**
     * The main entry point of GDMS, used to access the data source.
     */
    private final DataSourceFactory dsf;
    /**
     * Used to recover the metadata of the data source.
     */
    private Metadata edgesMetadata;
    /**
     * Used to log possible errors encountered when initializing the 
     * index.
     */
    private static final Logger LOGGER = Logger.getLogger(GDMSGraph.class);
    /**
     * Used to recover the set of vertices.
     */
    private HashSet<Integer> vertexSet = null;
    /**
     * Used to track the progress of the index initialization.
     */
    public final ProgressMonitor pm;
    /**
     * Index used to reference the start node field.
     */
    public int START_NODE_FIELD_INDEX = -1;
    /**
     * Index used to reference the end node field.
     */
    public int END_NODE_FIELD_INDEX = -1;
    /**
     * Index used to reference the weight field.
     */
    public int WEIGHT_FIELD_INDEX = -1;
    /**
     * Index used to reference the geometry field.
     */
    public int GEOMETRY_FIELD_INDEX = -1;

    // CONSTRUCTOR
    /**
     * Creates a (JGraphT) graph from an existing {@link DataSet}.
     *
     * @param dsf The {@link DataSourceFactory} used to parse the data set.
     * @param dataSet The data set.
     * @param pm The progress monitor used to track the progress of the index
     * initialization.
     * @throws DriverException
     */
    public GDMSGraph(DataSourceFactory dsf, DataSet dataSet, ProgressMonitor pm) throws DriverException {
        this.dataSet = dataSet;
        this.dsf = dsf;
        this.pm = pm;
        edgesMetadata = dataSet.getMetadata();
        initIndex();
    }

    /**
     * After checking if the data source is valid, builds indices
     * on the start and end nodes together, on the start nodes alone,
     * and on the end nodes alone.
     *
     * @throws DriverException
     */
    public void initIndex() throws DriverException {
        try {
            // First check if the data source is valid.
            if (checkMetadata()) {
                // If there is no index on the start and end nodes together,
                // then build one.
                if (!dsf.getIndexManager().isIndexed(
                        dataSet,
                        new String[]{GraphSchema.START_NODE, GraphSchema.END_NODE})) {
                            dsf.getIndexManager().buildIndex(
                                    dataSet, 
                                    new String[]{GraphSchema.START_NODE, GraphSchema.END_NODE},
                                    pm);
                }
                // If there is no index on the start node, then build one.
                if (!dsf.getIndexManager().isIndexed(
                        dataSet, 
                        GraphSchema.START_NODE)) {
                            dsf.getIndexManager().buildIndex(
                                    dataSet, 
                                    GraphSchema.START_NODE, 
                                    pm);
                }
                // If there is no index on the end node, then build one.
                if (!dsf.getIndexManager().isIndexed(
                        dataSet, 
                        GraphSchema.END_NODE)) {
                            dsf.getIndexManager().buildIndex(
                                    dataSet, 
                                    GraphSchema.END_NODE, 
                                    pm);
                }
            }
        } catch (DriverException ex) {
            LOGGER.error("Unable to get metadata.", ex);
        } catch (IndexException ex) {
            LOGGER.error("Unable to create index.", ex);
        } catch (NoSuchTableException ex) {
            LOGGER.error("Unable to find the table.", ex);
        }
    }

    /**
     * Checks the metadata of the data source to make sure we will
     * be able to use graph analysis tools on it.
     *
     * @throws DriverException
     */
    private boolean checkMetadata() throws DriverException {
        // edgesMetadata = dataSet.getMetadata(); // Moved to the constructor.
        
        // Recover the indices for the the_geom, start_node, and 
        // end_node fields.
        GEOMETRY_FIELD_INDEX = MetadataUtilities.getSpatialFieldIndex(edgesMetadata);
        START_NODE_FIELD_INDEX = edgesMetadata.getFieldIndex(GraphSchema.START_NODE);
        END_NODE_FIELD_INDEX = edgesMetadata.getFieldIndex(GraphSchema.END_NODE);
        
        // Make sure the start_node and end_node fields were found.
        if (START_NODE_FIELD_INDEX == -1) {
            throw new IllegalArgumentException("The table must contain a field named start_node");
        }
        if (END_NODE_FIELD_INDEX == -1) {
            throw new IllegalArgumentException("The table must contain a field named end_node");
        }
        
        // Everything seems okay: Exit!
        return true;
    }

    /**
     * Sets the name of the field that contains the weight value 
     * to process the graph.
     *
     * @param fieldName The name of the field that contains the weight value.
     * @throws DriverException
     */
    // TODO: Explain why this is separate from the other fields.
    // There exists a GraphSchema.WEIGHT String.
    public void setWeightFieldIndex(String fieldName) throws DriverException {
        int fieldIndex = edgesMetadata.getFieldIndex(fieldName);
        // Note: getFieldIndex(String fieldName) returns -1 if fieldName 
        // is not found.
        if (fieldIndex == -1) {
            throw new IllegalArgumentException("The table must contains a field named " + fieldName);
        }
        this.WEIGHT_FIELD_INDEX = fieldIndex;
    }

    /**
     * Returns the weight field index.
     *
     * @return The weight field index.
     */
    public int getWeightFieldIindex() {
        return WEIGHT_FIELD_INDEX;
    }

    /**
     * Returns the {@link Geometry} of a given {@link GraphEdge}.
     *
     * @param graphEdge The given {@link GraphEdge}.
     * @return The {@link Geometry} of this [@link GraphEdge}.
     * @throws DriverException
     */
    @Override
    public Geometry getGeometry(GraphEdge graphEdge) throws DriverException {
        return dataSet.getGeometry(graphEdge.getRowId(), GEOMETRY_FIELD_INDEX);
    }

    /**
     * Returns the {@link Geometry} located at a given row.
     * 
     * @param rowid The row id.
     * @return The {@link Geometry} located at this row.
     * @throws DriverException 
     */
    @Override
    public Geometry getGeometry(int rowid) throws DriverException {
        return dataSet.getGeometry(rowid, GEOMETRY_FIELD_INDEX);
    }

    /**
     * Returns a {@link Set} of (new) {@link GraphEdge} objects
     * corresponding to the edges in the data set from a given start vertex 
     * to a given end vertex if such edges exist; otherwise returns
     * {@link Collections.EMPTY_SET}.
     * 
     * @param startVertex The start vertex.
     * @param endVertex The end vertex.
     * @return The set of {@link GraphEdge}s from the start vertex
     * to the end vertex or {@link Collections.EMPTY_SET} if no such 
     * edges exist.
     */
    @Override
    public Set<GraphEdge> getAllEdges(Integer startVertex, Integer endVertex) {
        try {
            // Obtain an Iterator.
            Iterator<Integer> queryResult = getMultiIndexIterator(startVertex, endVertex);
            // Initialize a set to hold the edges.
            HashSet<GraphEdge> edges = new HashSet<GraphEdge>();
            // Add new GraphEdges to the set.
            while (queryResult.hasNext()) {
                Integer rowId = queryResult.next();
                edges.add(new GraphEdge(startVertex, endVertex, getWeightVertex(rowId), rowId));
            }
            // Return the set.
            return edges;
        } catch (DriverException ex) {
        }
        // If no edges were found, return the empty set.
        return Collections.EMPTY_SET;
    }

    /**
     * Returns a (new) {@link GraphEdge} object if an edge from
     * a given start vertex to a given end vertex exists
     * in the data set.
     * 
     * @param startVertex The start vertex.
     * @param endVertex The end vertex.
     * @return 
     */
    @Override
    public GraphEdge getEdge(Integer startVertex, Integer endVertex) {
        try {
            Iterator<Integer> queryResult = getMultiIndexIterator(startVertex, endVertex);
            Integer rowId = queryResult.next();
            return new GraphEdge(startVertex, endVertex,
                    getWeightVertex(rowId), rowId);
        } catch (DriverException ex) {
        }
        return null;
    }

    /**
     * Returns a new {@link ClassBasedEdgeFactory} based on the
     * {@link GraphEdge} class.
     * 
     * @return A new {@link ClassBasedEdgeFactory} based on the
     * {@link GraphEdge} class.
     */
    @Override
    public EdgeFactory<Integer, GraphEdge> getEdgeFactory() {
        return new ClassBasedEdgeFactory<Integer, GraphEdge>(GraphEdge.class);
    }

    /**
     * Adds an edge from a given start vertex to a given end vertex 
     * (NOT YET SUPPORTED!).
     *
     * @param startVertex The start vertex.
     * @param endVertex The end vertex.
     * @return The newly added GraphEdge.
     */
    // TODO: Implement this.
    @Override
    public GraphEdge addEdge(Integer startVertex, Integer endVertex) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Adds a given {@link GraphEdge} from a given start vertex to a given 
     * end vertex (NOT YET SUPPORTED!).
     *
     * @param startVertex The start vertex.
     * @param endVertex The end vertex.
     * @param e The GraphEdge to add.
     * @return True if the edge was successfully added.
     */
    // TODO: Implement this.
    @Override
    public boolean addEdge(Integer startVertex, Integer endVertex, GraphEdge e) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Adds a vertex to the graph (NOT YET SUPPORTED!).
     *
     * @param vertex The vertex to add.
     * @return {@code true} if the vertex was successfully added.
     */
    // TODO: Implement this.
    @Override
    public boolean addVertex(Integer vertex) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Returns {@code true} if the data set contains an edge from a given start
     * vertex to a given end vertex.
     * 
     * @param startVertex The start vertex.
     * @param endVertex The end vertex.
     * @return {@code true} if there exists an edge from the start vertex
     * to the end vertex.
     */
    @Override
    public boolean containsEdge(Integer startVertex, Integer endVertex) {
        try {
            Iterator<Integer> queryResult = getMultiIndexIterator(startVertex, endVertex);
            return queryResult.hasNext();
        } catch (DriverException ex) {
        }
        return false;
    }

    /**
     * Returns {@code true} if the data set contains a given {@link GraphEdge}.
     * 
     * @param graphEdge The {@link GraphEdge}.
     * @return {@code true} if the {@link GraphEdge} is contained in the data set.
     */
    @Override
    public boolean containsEdge(GraphEdge graphEdge) {
        return containsEdge(graphEdge.getSource(), graphEdge.getTarget());
    }

    /**
     * Returns {@code true} if the data set contains a given vertex.
     * 
     * @param vertex The vertex.
     * @return {@code true} if the vertex is contained in the data set.
     */
    @Override
    public boolean containsVertex(Integer vertex) {
        try {
            // Check in the start node column.
            Iterator<Integer> queryResult = getIndexIterator(GraphSchema.START_NODE, vertex);
            if (queryResult.hasNext()) {
                return true;
            } else {
                // Check in the end node column.
                queryResult = getIndexIterator(GraphSchema.END_NODE, vertex);
                if (queryResult.hasNext()) {
                    return true;
                } else {
                    return false;
                }
            }
        } catch (DriverException ex) {
        }
        return false;
    }

    /**
     * Returns a new {@link GraphEdgeSet} of the current
     * graph.
     * 
     * @return The set of edges of the graph.
     */
    @Override
    public Set<GraphEdge> edgeSet() {
        return new GraphEdgeSet(this);
    }

    /**
     * Returns the set of all edges touching the specified vertex, or
     * the empty set if no edges touch the vertex.
     * 
     * @param vertex The vertex to be examined.
     * @return The edges touching this vertex.
     */
    @Override
    public Set<GraphEdge> edgesOf(Integer vertex) {
        // Create an empty set to store the edges.
        HashSet<GraphEdge> edgesOf = new HashSet<GraphEdge>();
        try {
            // Recover the edges that start at the given vertex.
            Iterator<Integer> queryResult = getIndexIterator(GraphSchema.START_NODE, vertex);
            while (queryResult.hasNext()) {
                Integer rowId = queryResult.next();
                Integer dest = getTargetVertex(rowId);
                edgesOf.add(new GraphEdge(vertex, dest, getWeightVertex(rowId), rowId));
            }
            
            // Recover the edges that end at the given vertex.
            queryResult = getIndexIterator(GraphSchema.END_NODE, vertex);
            while (queryResult.hasNext()) {
                Integer rowId = queryResult.next();
                Integer source = getSourceVertex(rowId);
                edgesOf.add(new GraphEdge(source, vertex, getWeightVertex(rowId), rowId));
            }

            // Return the set of edges touching the given vertex.
            return edgesOf;
        } catch (DriverException ex) {
        }
        // The constructor of HashSet creates an empty set, 
        // so if no edges were added, then here we return the empty set.
        // TODO: Should we just return Collections.EMPTY_SET ?
        return edgesOf;
    }

    /**
     * Removes an edge beginning at a given start vertex and ending at a given
     * end vertex (NOT YET SUPPORTED!).
     *
     * @param startVertex The start vertex.
     * @param endVertex The end vertex.
     * @return The GraphEdge that was removed.
     */
    // TODO: Implement this.
    @Override
    public GraphEdge removeEdge(Integer startVertex, Integer endVertex) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Removes the specified edge from the graph (NOT YET SUPPORTED!).
     * 
     * @param graphEdge The {@link GraphEdge} to be removed.
     * @return {@code true} if and only if the graph contained the
     * specified edge.
     */
    // TODO: Implement this.
    @Override
    public boolean removeEdge(GraphEdge graphEdge) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Removes the specified vertex from this graph as well as all 
     * the edges that touch it if it is present (NOT YET SUPPORTED!).
     * 
     * @param vertex The vertex to be removed.
     * @return {@code true} if and only if the graph contained the
     * specified vertex.
     */
    @Override
    public boolean removeVertex(Integer vertex) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Checks to see if the vertexSet is null: if so, recovers all source and
     * target vertices into the vertexSet and returns the vertexSet; if not,
     * returns the vertexSet directly.
     *
     * @return The set of vertices located in the data source.
     */
    // Why does this method return Set<Integer> and not HashSet<Integer>?
    @Override
    public Set<Integer> vertexSet() {
        if (vertexSet == null) {
            vertexSet = new HashSet<Integer>();
            try {
                long rowCount = dataSet.getRowCount();
                for (int i = 0; i < rowCount; i++) {
                    Integer source = getSourceVertex(i);
                    Integer dest = getTargetVertex(i);
                    vertexSet.add(source);
                    vertexSet.add(dest);
                }
                return vertexSet;
            } catch (DriverException ex) {
            }
        }
        return vertexSet;
    }

    /**
     * Returns the source vertex of a given {@link GraphEdge}.
     * 
     * @param graphEdge The {@link GraphEdge}.
     * @return The source vertex.
     */
    @Override
    public Integer getEdgeSource(GraphEdge graphEdge) {
        return graphEdge.getSource();
    }

    /**
     * Returns the target vertex of a given {@link GraphEdge}.
     * 
     * @param graphEdge The {@link GraphEdge}.
     * @return The target vertex.
     */
    @Override
    public Integer getEdgeTarget(GraphEdge graphEdge) {
        return graphEdge.getTarget();
    }

    /**
     * Returns the weight of a given {@link GraphEdge}.
     * 
     * @param graphEdge The {@link GraphEdge}.
     * @return The weight.
     */
    @Override
    public double getEdgeWeight(GraphEdge graphEdge) {
        return graphEdge.getWeight();
    }

    /**
     * Returns the {@link Set} of edges that end at a given
     * vertex.
     * 
     * @param vertex The vertex.
     * @return The set of incoming edges.
     */
    public Set<GraphEdge> incomingEdgesOf(Integer vertex) {
        try {
            // Get an index iterator on the edges that end at
            // the given vertex.
            Iterator<Integer> queryResult = getIndexIterator(GraphSchema.END_NODE, vertex);
            if (queryResult.hasNext()) {
                // Initialize the set of incoming edges.
                HashSet<GraphEdge> incomingEdges = new HashSet<GraphEdge>();
                // Go through the edges that end at the given vertex.
                while (queryResult.hasNext()) {
                    // Obtain the source vertex of each edge that ends
                    // at the given index.
                    Integer rowId = queryResult.next();
                    Integer sourceVertex = getSourceVertex(rowId);
                    // Add a new GraphEdge object corresponding to
                    // this edge to the set of incoming edges.
                    incomingEdges.add(new GraphEdge(sourceVertex, vertex, getWeightVertex(rowId), rowId));
                }
                // Return the set of incoming edges.
                return incomingEdges;
            }
        } catch (DriverException ex) {
        }
        // If there are no incoming edges, return the empty set.
        return Collections.EMPTY_SET;
    }

    /**
     * Returns the {@link Set} of edges that start at a given
     * vertex.
     * 
     * @param vertex The vertex.
     * @return The set of outgoing edges.
     */
    public Set<GraphEdge> outgoingEdgesOf(Integer vertex) {
        try {
            // Get an index iterator on the edges that start at
            // the given vertex.
            Iterator<Integer> queryResult = getIndexIterator(GraphSchema.START_NODE, vertex);
            if (queryResult.hasNext()) {
                // Initialize the set of outgoing edges.
                HashSet<GraphEdge> outgoingEdges = new HashSet<GraphEdge>();
                // Go through the edges that start at the given vertex.
                while (queryResult.hasNext()) {
                    // Obtain the target vertex of each edge that starts
                    // at the given index.
                    Integer rowId = queryResult.next();
                    Integer targetVertex = getTargetVertex(rowId);
                    // Add a new GraphEdge object corresponding to
                    // this edge to the set of outgoing edges.
                    outgoingEdges.add(new GraphEdge(vertex, targetVertex, getWeightVertex(rowId), rowId));
                }
                // Return the set of outgoing edges.
                return outgoingEdges;
            }
        } catch (DriverException ex) {
        }
        // If there are no outgoing edges, return the empty set.
        return Collections.EMPTY_SET;
    }

    /**
     * Returns an {@link Iterator} on the indices of all values
     * of a given field.
     *
     * @param fieldToQuery The desired field to query.
     * @param valueToQuery The desired value to query.
     * @return An iterator on the indices.
     * @throws DriverException
     */
    public Iterator<Integer> getIndexIterator(String fieldToQuery, Integer valueToQuery) throws DriverException {
        DefaultAlphaQuery defaultAlphaQuery = new DefaultAlphaQuery(
                fieldToQuery, ValueFactory.createValue(valueToQuery));
        return dataSet.queryIndex(dsf, defaultAlphaQuery);
    }

    /**
     * Returns an {@link Iterator} on the indices of all edges
     * that begin at a given start node and end at a given end node.
     *
     * @param startNode The start node.
     * @param endNode The end node.
     * @return An iterator on the indices.
     * @throws DriverException
     */
    public Iterator<Integer> getMultiIndexIterator(Integer startNode, Integer endNode) throws DriverException {
        DefaultAlphaQuery defaultAlphaQuery = new DefaultAlphaQuery(
                    new String[]{GraphSchema.START_NODE, GraphSchema.END_NODE},
                    ValueFactory.createValue(new Value[]{
                        ValueFactory.createValue(startNode), 
                        ValueFactory.createValue(endNode)
                    })
                );
        return dataSet.queryIndex(dsf, defaultAlphaQuery);
    }

    /**
     * Returns the source vertex for a given edge defined by its row 
     * id in the dataset.
     *
     * @param rowId The row id.
     * @return The source vertex of the given edge.
     * @throws DriverException
     */
    private int getSourceVertex(long rowId) throws DriverException {
        return dataSet.getInt(rowId, START_NODE_FIELD_INDEX);
    }

    /**
     * Returns the target vertex for a given edge defined by its row 
     * id in the dataset.
     * 
     * @param rowId The row id.
     * @return The target vertex of the given edge.
     * @throws DriverException
     */
    private int getTargetVertex(long rowId) throws DriverException {
        return dataSet.getInt(rowId, END_NODE_FIELD_INDEX);
    }

    /**
     * Returns the weight of an edge defined by its row 
     * id in the dataset.
     *
     * @param rowId The row id.
     * @return The weight of the given edge.
     * @throws DriverException
     */
    private double getWeightVertex(long rowId) throws DriverException {
        return dataSet.getDouble(rowId, WEIGHT_FIELD_INDEX);
    }

    /**
     * Returns the indegree of a vertex (the number of head endpoints 
     * adjacent to the vertex).
     *
     * @param vertex The vertex.
     * @return The indegree of the vertex.
     * @throws DriverException  
     */
    public int inDegreeOf(Integer vertex) throws DriverException {
        try {
            Iterator<Integer> queryResult = getIndexIterator(GraphSchema.END_NODE, vertex);
            int counter = 0;
            while (queryResult.hasNext()) {
                queryResult.next();
                counter++;
            }
            return counter;
        } catch (DriverException ex) {
        }
        return 0;
    }

    /**
     * Returns the outdegree of a vertex (the number of tail endpoints 
     * adjacent to the vertex).
     * 
     * @param vertex The vertex.
     * @return The outdegree of the vertex.
     * @throws DriverException  
     */
    public int outDegreeOf(Integer vertex) throws DriverException {
        try {
            Iterator<Integer> queryResult = getIndexIterator(GraphSchema.START_NODE, vertex);
            int counter = 0;
            while (queryResult.hasNext()) {
                queryResult.next();
                counter++;
            }
            return counter;
        } catch (DriverException ex) {
        }
        return 0;
    }

    /**
     * Returns the values of all fields at a given row.
     * 
     * @param rowid The row id.
     * @return The values of all fields at the specified row in
     * a {@link Values[]}.
     * @throws DriverException 
     */
    @Override
    public Value[] getValues(int rowid) throws DriverException {
        return dataSet.getRow(rowid);
    }

    /**
     * Returns the number of rows in the data set.
     * 
     * @return The number of rows in the data set.
     * @throws DriverException 
     */
    @Override
    public long getRowCount() throws DriverException {
        return dataSet.getRowCount();
    }

    /**
     * Creates a new {@link GraphEdge} based on the values stored 
     * in the input dataset at the given index.
     *
     * @param index The index value.
     * @return The new {@link GraphEdge} from the given index.
     * @throws DriverException
     */
    public GraphEdge getGraphEdge(long index) throws DriverException {
        return new GraphEdge(
                getSourceVertex(index), 
                getTargetVertex(index), 
                getWeightVertex(index), 
                index);
    }
}