**Mars** - **M**olecule **AR**chive **S**uite - A framework for storage and reproducible processing of single-molecule datasets.

This repository contains adapted versions of the SCIFIO Micromanager format and translator. The changes ensure more fields are processed including the addition of MapAnnotations for plane specific fields. If Z > T, dimensions are swapped if the Preference setting CheckZvsTIME is test to true. This feature can be turned off using the follow script in.

```groovy
#@ PrefService prefService
prefService.setBoolean(MarsMicromanagerFormat.class, "CheckZvsTIME", false)
```

Mars documentation can be found at https://duderstadt-lab.github.io/mars-docs/
