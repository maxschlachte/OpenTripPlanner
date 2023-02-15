package org.opentripplanner.updater.permissions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.locationtech.jts.geom.Polygon;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionsPushUpdater {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionsPushUpdater.class);
  private static List<StreetEdge> touchedEdges = new ArrayList<>();
  private final Graph graph;

  public PermissionsPushUpdater(Graph graph) {
    this.graph = graph;
  }

  public static List<StreetEdge> getTouchedEdges() {
    return touchedEdges;
  }

  public static void resetAllPermissions() {
    // reset all previously modified edges
    for (StreetEdge edge : touchedEdges) {
      edge.resetPermission();
    }
    touchedEdges.removeAll(touchedEdges);
  }

  private enum METHOD {
    SET,
    ADD,
    REMOVE,
  }

  public int setPermissions(
    Polygon polygon,
    StreetTraversalPermission permission,
    StreetTraversalPermission originalPermission
  ) {
    LOG.info("run permission push updater and set permission to all edges in polygon.");
    return modifyPermissions(polygon, permission, originalPermission, METHOD.SET);
  }

  public int removePermissions(
    Polygon polygon,
    StreetTraversalPermission permission,
    StreetTraversalPermission originalPermission
  ) {
    LOG.info("run permission push updater and remove permission from all edges in polygon.");
    return modifyPermissions(polygon, permission, originalPermission, METHOD.REMOVE);
  }

  public int addPermissions(
    Polygon polygon,
    StreetTraversalPermission permission,
    StreetTraversalPermission originalPermission
  ) {
    LOG.info("run permission push updater and add permission to all edges in polygon.");
    return modifyPermissions(polygon, permission, originalPermission, METHOD.ADD);
  }

  private int modifyPermissions(
    Polygon polygon,
    StreetTraversalPermission permission,
    StreetTraversalPermission originalPermission,
    METHOD method
  ) {
    int numOfUpdates = 0;
    // get collection of all StreetEdges in graph
    Collection<StreetEdge> streetEdges = graph.getStreetEdges();
    // set permissions for edges in polygon
    for (StreetEdge edge : streetEdges) {
      boolean filterByOriginalPermission = true;
      if (originalPermission != null) {
        filterByOriginalPermission = edge.getOriginalPermission().allows(originalPermission);
      }
      if (
        polygon.contains(edge.getGeometry()) &&
        edge.getPermission() != permission &&
        filterByOriginalPermission
      ) {
        numOfUpdates += 1;
        String oldPerm = edge.getPermission().toString();
        switch (method) {
          case SET -> edge.setPermission(permission);
          case ADD -> edge.setPermission(edge.getPermission().add(permission));
          case REMOVE -> edge.setPermission(edge.getPermission().remove(permission));
        }
        LOG.info(
          "{} permission '{}': '{}' to '{}' for edge '{}'.",
          method.toString(),
          permission.toString(),
          oldPerm,
          edge.getPermission().toString(),
          edge
        );
        if (!touchedEdges.contains(edge)) {
          touchedEdges.add(edge);
        }
      }
    }
    return numOfUpdates;
  }
}
