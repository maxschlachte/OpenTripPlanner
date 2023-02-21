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

This fork intends to offer an API for manipulating the graph by changing the permissions of the edges in a given polygon.

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
              [12...,50...],
              ...
              [12...,50...]
            ]
          ]
        },
        "properties": {
          "name": "exclusion zone #1",
          "method": "REMOVE",
          "permission": "car",
          "originalPermission": "car",
          "strict":false
        }
      },
      {
        "type": "Feature",
        "geometry": {
          "type": "Polygon",
          "coordinates": [
            [
              [12...,50...],
              ...
              [12...,50...]
            ]
          ]
        },
        "properties": {
          "name": "experimental shared space",
          "method": "ADD",
          "permission": "car",
          "originalPermission": "pedestrian",
          "strict":false
        }
      }
    ]
  }'
```
