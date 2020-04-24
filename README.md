# ubirch-key-exporter

Simple application to read keys from Neo4Js and import them in the identity service.

## Purpose
 
The purpose of this code is to migrate public keys from the key service into the new identity service.


## How to run

0- `It is important that you select the proper values for your tools`:

You can do this by looking at: *src/main/resources/application.base.conf*

In general, you will need the following env var:

```shell
* _NEO_PASS_ : The password to get connected to Neo4j
* _NEO_USER_ : The username to get connected to Neo4j
* _NEO_URI_ : The server uri for Neoj
* _IDENTITY_SERVER_BASE_ : The identity service principal path. Do not add a trailing slash.
* _REPORT_FILE_BASE_ : The folder path for the reports. Do not add a trailing slash. Note that 
the name is provided by the system.
```

1- `Compile and package the project`.

First of all, compile the project, by running the following command.

```shell
mvn clean compile
```

This command will clean all possible existing compiled  resources and compile both normal classes.

The previous command helps in making sure the system compiles, now we need to package it:

```shell
mvn package
```

If all goes well, we should be able to start the tool.

2- `Run the tool`:
    
```shell
  java -cp target/key_exporter-0.5.jar com.ubirch.Service 
```

Of course, you can also run the system from your IDE. Just don't forget to put the env vars before running the system.
Another way of running it is by modifying the _application.conf_.

3- `Check reports`:

After having run the tool you should see two reports per run. An executive report (summary) and a detailed report 
of the data processed. The format that is used is CSV.

Example names: 

```shell
key_export_report_3448f99b-b8b2-467a-a17b-ef9739c20e3d_2020-04-24T10:52:01.390Z.results.csv
key_export_report_3448f99b-b8b2-467a-a17b-ef9739c20e3d_2020-04-24T10:52:01.390Z.summary.csv
```
