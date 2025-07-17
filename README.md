# fhirpath-tester

Run it using docker or `mvn spring-boot:run`

Build it with
`mvn clean install`

## Changelog

### 17 July 2025
* Update to the HAPI 8.2.0 engine (not planning on continuing this, but playing with CQL)

### 23 August 2024
* Update to the HAPI 7.4.0 engine
*(Note this may be the last update as moving to a version that does not require the HAPI level libraries)*

### 18 May 2024
* Update to the HAPI 7.2.0 engine

### 2 November 2023
* Update to the HAPI 6.8.5 engine
* Add support for the FHIR Mapping Engine
* Share the worker context property more widely (more efficient use of memory)

### 7 September 2023
* Correct null reference exception when processing variables that have no value provided

### 21 August 2023
* Update to the HAPI 6.8.0 engine
* Use the newly added capacity to serialize fragments to JSON so that returning backbone elements is now possible
