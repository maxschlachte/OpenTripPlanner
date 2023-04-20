## Overview

OpenTripPlanner (OTP) is an open source multi-modal trip planner, focusing on travel by scheduled
public transportation in combination with bicycling, walking, and mobility services including bike
share and ride hailing. Its server component runs on any platform with a Java virtual machine (
including Linux, Mac, and Windows). It exposes REST and GraphQL APIs that can be accessed by various
clients including open source Javascript components and native mobile applications. It builds its
representation of the transportation network from open data in open standard file formats (primarily
GTFS and OpenStreetMap). It applies real-time updates and alerts with immediate visibility to
clients, finding itineraries that account for disruptions and service changes.

Note that this branch contains **OpenTripPlanner 2**, the second major version of OTP, which has
been under development since Q2 2018. The latest version of OTP is v2.2.0, released in November 2022.

If you do not want to test or explore this version, please switch to the final 1.x release
tag `v1.5.0` or the `dev-1.x` branch for any patches and bugfixes applied to the v1.5.0 release.

### Dockerize

The maven project can be dockerized for local use by using Jib:

```
sudo mvn jib:dockerBuild
```

### Permission API

This fork intends to offer an API for manipulating the graph by changing the permissions of the edges in a given polygon. Requests must be directed to the `/otp/permissions` endpoint.

#### Set permissions (POST)

In order to change the permissions of edges a polygon and the operation properties must be given. The polygon follows geo-json standard and is delivered as a feature collection, also including the properties.
The `properties` attribute includes a descriptive `name`, the `method` (set, add, remove) of the operation and the `permission` (pedestrian, bicycle or car). Depending on the method, the permission is applied to the edges in the polygon:
* SET: Overwrites the current permission with the one given.
* ADD: Adds the given permission to the current permission.
* REMOVE: Removes the given permission from the current permission.

<span style="font-size:x-small; font-weight:bold; font-style:italic">
Request:</span>

```
curl --location --request POST 'http://localhost:8080/otp/permissions' --header 'Content-Type: application/json' --data-raw '{
    "type": "FeatureCollection",
    "features": [
      {
        "type": "Feature",
        "geometry": {
          "type": "Polygon",
          "coordinates": [
            [
              [12.47972,50.71456],
              [12.48019,50.71456],
              [12.48019,50.71321],
              [12.47972,50.71321],
              [12.47972,50.71456]
            ]
          ]
        },
        "properties": {
          "name": "road closure Reichenbacher Straße, Zwickau beneath railroad bridge",
          "method": "REMOVE",
          "permission": "car"
        }
      }
    ]
}'
```

<span style="font-size:x-small; font-weight:bold; font-style:italic">
Response:</span>

```
{
    "operations": [
        {
            "requestName": "road closure Reichenbacher Straße, Zwickau beneath railroad bridge",
            "method": "REMOVE",
            "message": "Updated the permission of 2 edges with permission 'CAR'",
            "updates": 2
        }
    ],
    "processingTimeInSeconds": 4.95,
    "message": "Permissions modified, 2 edges updated!"
}
```


#### get permissions (GET)

To get all discrepancies between current edge permissions and their original permissions issue a get request to the endpoint:

<span style="font-size:x-small; font-weight:bold; font-style:italic">
Request:</span>

```
curl --location --request GET 'http://localhost:8080/otp/permissions'
```

<span style="font-size:x-small; font-weight:bold; font-style:italic">
Response:</span>

```
{
    "Reichenbacher Straße": {
        "originalPermission": "ALL",
        "currentPermission": "PEDESTRIAN_AND_BICYCLE"
    }
}
```

#### reset permissions (DELETE)

To reset all permissions issue a delete request to the endpoint:

<span style="font-size:x-small; font-weight:bold; font-style:italic">
Request:</span>

```
curl --location --request DELETE 'http://localhost:8080/otp/permissions'
```

<span style="font-size:x-small; font-weight:bold; font-style:italic">
Response:</span>

```
{
    "message": "Permissions were reset."
}
```