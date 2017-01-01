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

Idiom Mining
----
This repository contains the code related to the paper:
```
@inproceedings{allamanis2014mining,
  title={Mining Idioms from Source Code},
  author={Allamanis, Miltiadis and Sutton, Charles},
  booktitle={Proceedings of the 22nd ACM SIGSOFT International Symposium on Foundations of Software Engineering},
  pages={472--483},
  year={2014},
  organization={ACM}
}
```
To train a TSG for Java use the main class in `codemining.lm.tsg.tui.java.SampleBlockedTSG` with the arguments
```
/path/to/folder binaryvariables  filterblock 1.0 50
```
to run the TSG training as in the "Mining Idioms from Source Code" paper. For other options please explore the code.
