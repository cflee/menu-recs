# menu-recs
> Demo menu recommendations using CPLEX constraint optimization

IS421 Enterprise Analytics for Decision Support

## Pre-requisites
- IBM ILOG CPLEX Optimization Studio 12.6.1

This has only been tested on macOS, but it should work on Windows or Linux as well.

(CPLEX Optimization Studio used under license from the IBM Academic Initiative program.
We don't distribute anything from CPLEX here. BYO!)

## Build
We use maven-assembly-plugin to make a fat JAR (except the cplex JAR).

```
mvn clean package
```

## Usage
Run the system using:

```
java -cp /Users/cflee/Applications/IBM/ILOG/CPLEX_Studio1261/cplex/lib/cplex.jar:menu-recs-1.0-jar-with-dependencies.jar \
  -Djava.library.path=/Users/cflee/Applications/IBM/ILOG/CPLEX_Studio1261/cplex/bin/x86-64_osx/ \
  menurecs.MenuEngine
```

You will need to replace the path to `cplex.jar` and to the `cplex` bin folder.
(On macOS, that `x86-64_osx` folder contains a bunch of dylib files and the actual executables.)
