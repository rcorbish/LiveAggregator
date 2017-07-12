#!/bin/sh

gradle javadoc
rsync -r build/docs/javadoc amd215:docs
