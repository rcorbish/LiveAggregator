# LiveAggregator
Perform real-time aggregation of data feeds
The poor (wo)man's active pivot :). It's simple enough and is pretty fast (if I say so myself)
The config. allows support to setup new views for clients.

Class docs & APIS are [here](https://rcorbish.ydns.eu/live-aggregator-docs/)

# How it works

Implemented in pure java (v8 reqd), you need to feed the monster DataElements.
DataElements represent a single number and a bunch of labels describing the content. It's
a java implementation of a star-schema. Because we are performing real-time aggregation
of these numbers, it's expected to keep the number of labels manageable. Everything is kept in
memory for optimum performance, so balance the data- complexity and -volume with the
server configuration.

A DataElement is an instance of the data and it's associated labels. Build one and call
Aggregator.process to add to the collection

# Pipelining

This is a 3 stage pipeline - always forward progressing to allow for future distribution

## Cache

This holds the most recent copy of a DataElement. Each DataElement has an invariantKey, which
is used to identify changes to existing content. This cache is used to determine whether an
element is new or changed.

## Data View

This holds a representation of all defined views, whether or not there is one 'in use'. It
can be thought of as a pivot table, with all rows and columns fully expanded. When a new (or changed)
element is present to this view - all affected cells have thir values adjusted. The only 
complexity here is mapping DataElements to the view definitions (filters, rows, columns, etc.)

## Client Proxy

This is the representation of what a client is looking at. Each active client has a client view
instance to handle messaging. When a DataView presents an updated element to the client view,
the client view is responsible for determining whether that element is 'on screen'. This amounts
to figuring out whether the appropriate rows and columns are expanded. It is expected that
a client 'looking at' real-time data would not have a complex (10K+) amount of cells on screen and
that each client view is small. Again, balance the amount of data on-screen with the capacity of the
servers.

That's pretty much it:
* receive a DataElement
* Determine if it's a replacement or new
* Notify all affected views
* Update aggregate (totals)
* Notify all affected clients
* send UPD messages to clients

# Client Display

The supported display is a web page, connected to the Client Proxy using webscokets.
The protocol is implemented in json

# Installation

## preferred means is to build from source

### Download

Get the source:

`git clone https://github.com/rcorbish/LiveAggregator.git`

Or a zipped file from [here](https://github.com/rcorbish/LiveAggregator/archive/master.zip)

### Build

Use one of these ...

* build in eclipse
* mvn build
* gradle build

## or use a docker image 

### Docker image

load a docker image from the hub
 
`docker pull rcorbish/live-aggregator`

 


