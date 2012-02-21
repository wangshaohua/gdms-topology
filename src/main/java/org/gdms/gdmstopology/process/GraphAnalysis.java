/**
 * GDMS-Topology  is a library dedicated to graph analysis. It's based on the JGraphT available at
 * http://www.jgrapht.org/. It ables to compute and process large graphs using spatial
 * and alphanumeric indexes.
 * This version is developed at French IRSTV institut as part of the
 * EvalPDU project, funded by the French Agence Nationale de la Recherche
 * (ANR) under contract ANR-08-VILL-0005-01 and GEBD project
 * funded by the French Ministery of Ecology and Sustainable Development .
 *
 * GDMS-Topology  is distributed under GPL 3 license. It is produced by the "Atelier SIG" team of
 * the IRSTV Institute <http://www.irstv.cnrs.fr/> CNRS FR 2488.
 *
 * GDMS-Topology is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * GDMS-Topology is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GDMS-Topology. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://trac.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */
package org.gdms.gdmstopology.process;

import java.util.List;
import org.gdms.gdmstopology.model.GraphEdge;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.WeightedGraph;

/**
 *
 * @author ebocher
 */
public class GraphAnalysis {

        //Constants used in the functions
        public static int DIRECT = 1;
        public static int DIRECT_REVERSED = 2;
        public static int UNDIRECT = 3;

        /**
         * Some usefull methods to analyse a graph network.
         */
        private GraphAnalysis() {
        }

        public static List<GraphEdge> getShortestPath(WeightedGraph<Integer, GraphEdge> graph,
                Integer startNode, Integer endNode) {
                return DijkstraShortestPath.findPathBetween(graph, startNode, endNode);
        }
}
