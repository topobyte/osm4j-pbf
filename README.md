# Repository moved

This content of this repository has been merged into
[topobyte/osm4j](https://github.com/topobyte/osm4j), a single
repository for all osm4j modules.

# About

This is the osm4j module for reading and writing OSM data in the PBF format.

Have a look at the main [osm4j repository](https://github.com/topobyte/osm4j) or
the [project homepage](http://www.jaryard.com/projects/osm4j/index.html) for
information about the library osm4j in general.

## License

This library is released under the terms of the GNU Lesser General Public
License.

See [LGPL.md](LGPL.md) and [GPL.md](GPL.md) for details.

## Third party code

The code in the `crosby.binary` packages from Scott A. Crosby 
has been imported from this repository:

https://github.com/scrosby/OSM-binary

Which is released under the LGPL.

# Hacking

## Generating the protocol buffers source

    protoc --java_out lite:core/src/gen/java res/proto/*
    protoc --java_out full/src/gen/java res/proto/*
