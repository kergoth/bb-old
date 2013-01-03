General
-------

- Show all types of dependency, not just `DEPENDS`, in 'showdepends' and
  'whatdepends'
- Add a subcommand which emits information about all dependencies, indirect
  and direct, runtime and build time, in both directions for any target
- Add additional types of variable filtering for `show`

    - filter out 'unused' variables (not used by any tasks for the recipe)
    - variables with documentation ('doc' flag)
    - variables with defined variable types ('type' flag)
    - variables with default values (??=, aka 'defaultval' flag)

- Add interactive mode

    - single up-front recipe parse step
    - add reparse subcommand

Code
----

- Avoid use of `build_targets`, so commands like showdepends work against any
  target, not just `build_targets` (e.g. bb showdepends virtual/libc should
  work)
- Restructure commands, move them out of 'bb'
- Move bb.main into the python package

Long term
---------

- Add a `bake` command, to do a build by spawning off a bitbake UI

Considering
-----------

- Wildcards for `show` via fnmatch
- Shift bitbake-layers into a subcommand
- Support emission of structured data. E.g. `bb dependsinfo TARGET` could emit
  JSON or colon separated information about all the dependencies in both
  directions for it, rather than the more specific, single type of data shown
  by showdepends/whatdepends/etc
- Support showing additional information after builds. Potentially leverage
  buildstats, the license manifests, buildhistory, pkgdata, etc

- Show how a variable got defined to what it is (to do this, we need Peter
  Seebach's variable tracking patches, or we need to extract this information
  from the AST up front in the parse process)
- Show what the user can set in PACKAGECONFIG, either generally or per recipe
- Consider analyzing `base_contains()` calls to determine possible values for
  the various FEATURES and PACKAGECONFIG variables

Requirements of others
----------------------

- Variable inspection

    - what the variable is for
    - what recipes use it
    - what values are permitted
    - what variables it uses
    - whether the variable is used in the current configuration

- Find recipe

    - Text search of recipe name/summary/description
    - Text search of 'files contributed to the package or images'

        - Not doable before a build, only after

    - Package name
    - File location on target system

        - Not doable before a build, only after

- Inspect recipe

    - Runtime dependencies
    - Build dependencies
    - Reverse build and runtime dependencies
    - Why this recipe is being included in an image
    - Alternate versions
    - Bbappends
    - Layer overrides
