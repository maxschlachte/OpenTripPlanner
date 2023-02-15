package org.opentripplanner.api.resource;

import static org.opentripplanner.util.geometry.GeometryUtils.convertGeoJsonToJtsGeometry;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.security.PermitAll;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.time.StopWatch;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;
import org.json.simple.JSONObject;
import org.locationtech.jts.geom.Polygon;
import org.opentripplanner.standalone.api.OtpServerRequestContext;
import org.opentripplanner.routing.edgetype.StreetTraversalPermission;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.updater.pushupdater.PermissionsUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This REST API endpoint returns some meta-info about custom permissions and sets new permissions.
 * <p>
 * The HTTP verbs are used as follows:
 * <p>
 * GET - see the custom permissions as JSON
 * <p>
 * POST - send GeoJSON FeatureCollection with polygons and a "permission" property to this endpoint in order to set all edges within the given polygons to the defined permission
 * <p>
 * The HTTP request URLs are of the form /otp/permissions.
 */
@Path("/permissions")
@PermitAll // exceptions on methods
public class Permissions {

  public static final ObjectMapper mapper = new ObjectMapper();

  static {
    mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
    mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
  }
  private static final Logger LOG = LoggerFactory.getLogger(Permissions.class);
  private final PermissionsUpdater permissionsPushUpdater;

  public Permissions(@Context OtpServerRequestContext serverContext) {
    this.permissionsPushUpdater = new PermissionsUpdater(serverContext.graph());
  }

  private enum METHOD {
    ADD,
    REMOVE,
    SET
  }

  /**
   * Return the information about all edge changes.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getPermissions(@PathParam("edge") String edgeParam) {
    List<StreetEdge> touchedEdges = PermissionsUpdater.getTouchedEdges();
    Map<String, Map<String, String>> touchedEdgesObj = new HashMap<>();
    for (StreetEdge edge : touchedEdges) {
      Map<String, String> info = new HashMap<>();
      info.put("currentPermission", edge.getPermission().name());
      info.put("originalPermission", edge.getOriginalPermission().name());
      touchedEdgesObj.put(edge.toString(), info);
    }
    try {
      String json = mapper.writeValueAsString(touchedEdgesObj);
      return Response.ok(json).build();
    } catch (JsonProcessingException e) {
      throw new BadRequestException("Unable to serve JSON: " + e.getMessage());
    }
  }

  /**
   * Resets permissions for all edges.
   */
  @DELETE
  @Path("/reset")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response resetPermissions(
    HashMap<String, Object> body,
    @HeaderParam("OTPMaxResolves") @DefaultValue("1000000") int maxResolves,
    @Context HttpHeaders headers
  ) {
    PermissionsUpdater.resetAllPermissions();
    Map<String, Object> responseObj = new HashMap<>();
    responseObj.put("message", "Permissions were reset.");
    String responseJson = "{}";
    try {
      responseJson = mapper.writeValueAsString(responseObj);
    } catch (Exception e) {
      LOG.warn("Unable to parse post response: {}", e.getMessage());
    }
    return Response.ok(responseJson).build();
  }

  /**
   * Sets permissions for edges.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response postPermissions(
    HashMap<String, Object> body,
    @HeaderParam("OTPMaxResolves") @DefaultValue("1000000") int maxResolves,
    @Context HttpHeaders headers
  ) {
    Map<String, Object> responseObj;
    String responseJson;
    try {
      responseObj = executeRequest(body);
    } catch (Exception e) {
      throw new BadRequestException("Unable to serve JSON: " + e.getMessage());
    }
    try {
      responseJson = mapper.writeValueAsString(responseObj);
    } catch (Exception e) {
      LOG.warn("Unable to parse response: {}", e.getMessage());
      throw new ServerErrorException("Unable to parse json: " + e.getMessage(), 500);
    }
    return Response.ok(responseJson).build();
  }

  private Map<String, Object> executeRequest(HashMap<String, Object> body) throws Exception {
    // define property constants
    final String PROPERTY_NAME = "name";
    final String PROPERTY_PERMISSION = "permission";
    final String PROPERTY_METHOD = "method";
    final String PROPERTY_ORIGINAL_PERMISSION = "originalPermission";
    // define messages list
    List<Map<String, Object>> messages = new ArrayList<>();
    // check if body was passed and has feature key
    if (body == null || !body.containsKey("features")) {
      LOG.debug("No features found in body");
      throw new BadRequestException("No features found in body");
    }
    // start stopwatch and count of modifications
    int numOfOverallEdgeUpdates = 0;
    StopWatch watch = new StopWatch();
    watch.start();
    // processing body
    LOG.info("Retrieved body of type {}: {}", body.getClass(), body);
    GeoJsonObject geoJson = mapper.readValue((new JSONObject(body)).toString(), GeoJsonObject.class);
    LOG.info("Retrieved geoJson: {}", geoJson);
    if (geoJson instanceof FeatureCollection) {
      FeatureCollection featureCollection = (FeatureCollection) geoJson;
      for(Feature feature : featureCollection.getFeatures()) {
        StreetTraversalPermission permission = parseFeatureForPermission(feature, PROPERTY_PERMISSION);
        if (permission == null) {
          String permissionStr = feature.getProperty(PROPERTY_PERMISSION);
          throw new BadRequestException("Unable to parse permission property '" + permissionStr + "', must contain PEDESTRIAN, BICYCLE or CAR!");
        }
        METHOD method = parseFeatureToMethod(feature, PROPERTY_METHOD);
        if (method == null) {
          String methodStr = feature.getProperty(PROPERTY_METHOD);
          throw new BadRequestException("Unable to parse permission property '" + methodStr + "', must be either SET, ADD or REMOVE");
        }
        // if the permission is NONE, and it is issued on an ADD or REMOVE operation skip any further action since nothing should be changed logically
        if (permission == StreetTraversalPermission.NONE && (method == METHOD.ADD || method == METHOD.REMOVE)) {
          Map<String, Object> messageObj = new HashMap<>();
          String requestName = feature.getProperty(PROPERTY_NAME) != null ? feature.getProperty(PROPERTY_NAME) : "unknown";
          if (method == METHOD.ADD) {
            messageObj.put("requestName", requestName);
            messageObj.put("message", "Adding the permission 'NONE' to edges does not modify the edge permissions.");
            messageObj.put("updates", 0);
            messageObj.put("method", method.toString());
          } else if (method == METHOD.REMOVE) {
            messageObj.put("requestName", requestName);
            messageObj.put("message", "Removing the permission 'NONE' from edges does not modify the edge permissions.");
            messageObj.put("updates", 0);
            messageObj.put("method", method.toString());
          }
          messages.add(messageObj);
        } else {
          int numOfUpdates = switch (method) {
            case REMOVE -> permissionsPushUpdater.removePermissions(
              (Polygon) convertGeoJsonToJtsGeometry(feature.getGeometry()),
              permission,
              parseFeatureForPermission(feature, PROPERTY_ORIGINAL_PERMISSION)
            );
            case ADD -> permissionsPushUpdater.addPermissions(
              (Polygon) convertGeoJsonToJtsGeometry(feature.getGeometry()),
              permission,
              parseFeatureForPermission(feature, PROPERTY_ORIGINAL_PERMISSION)
            );
            case SET -> permissionsPushUpdater.setPermissions(
              (Polygon) convertGeoJsonToJtsGeometry(feature.getGeometry()),
              permission,
              parseFeatureForPermission(feature, PROPERTY_ORIGINAL_PERMISSION)
            );
            default -> 0;
          };
          Map<String, Object> messageObj = new HashMap<>();
          String requestName = feature.getProperty(PROPERTY_NAME) != null ? feature.getProperty(PROPERTY_NAME) : "unknown";
          messageObj.put("requestName", requestName);
          messageObj.put("message", "Updated the permission of " + numOfUpdates + " edges with permission '" + permission.name() + "'");
          messageObj.put("updates", numOfUpdates);
          messageObj.put("method", method.toString());
          messages.add(messageObj);
          numOfOverallEdgeUpdates = numOfOverallEdgeUpdates + numOfUpdates;
        }
      }
    }
    watch.stop();
    LOG.info("Processing time: {}", watch.getTime());
    Map<String, Object> responseObj = new HashMap<>();
    responseObj.put("operations", messages);
    responseObj.put("message", "Permissions modified, " + numOfOverallEdgeUpdates + " edges updated!");
    responseObj.put("processingTimeInSeconds", watch.getTime() * 0.001);
    return responseObj;
  }

  private METHOD parseFeatureToMethod(Feature feature, String propertyName) {
    String methodStr = feature.getProperty(propertyName);
    String methodStrNormalized = methodStr.toLowerCase();
    return switch (methodStrNormalized) {
      case "put", "add" -> METHOD.ADD;
      case "delete", "remove" -> METHOD.REMOVE;
      case "set", "create", "post" -> METHOD.SET;
      default -> null;
    };
  }

  private StreetTraversalPermission parseFeatureForPermission(Feature feature, String propertyName) {
    // parse permission
    String permissionStr = feature.getProperty(propertyName);
    StreetTraversalPermission permission = null;
    String permissionStrNormalized = permissionStr.toLowerCase().replaceAll(" ", "");
    if (permissionStrNormalized.equals("none")) {
      permission = StreetTraversalPermission.NONE;
      return permission;
    }
    if (permissionStrNormalized.equals("all")) {
      permission = StreetTraversalPermission.ALL;
      return permission;
    }
    if (permissionStrNormalized.contains("pedestrian")) {
      permission = addPermission(permission, StreetTraversalPermission.PEDESTRIAN);
    }
    if (permissionStrNormalized.contains("bicycle")) {
      permission = addPermission(permission, StreetTraversalPermission.BICYCLE);
    }
    if (permissionStrNormalized.contains("car")) {
      permission = addPermission(permission, StreetTraversalPermission.CAR);
    }
    return permission;
  }

  private StreetTraversalPermission addPermission(StreetTraversalPermission current, StreetTraversalPermission additional) {
    return current == null ? additional : current.add(additional);
  }
}
