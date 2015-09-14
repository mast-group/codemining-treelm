codemining-treelm
===============
codemining-treelm contains code for language models that work on trees.

`codemining.ast` contains code to convert ASTs to language-agnostic TreeNodes

`codemining.lm` contains an implementation of PCFGs and TSGs as well as some idiom-related code.


The project depends on three internal (maven) modules:

a) [codemining-utils](https://github.com/mast-group/codemining-utils)
b) [codemining-core](https://github.com/mast-group/codemining-core)
c) [codemining-sequencelm](https://github.com/mast-group/codemining-sequencelm)

The rest of the dependencies are declared in the maven dependencies. 
