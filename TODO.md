General
-------

- Handle all types of dependencies

    - Build time providers (`DEPENDS`/`PROVIDES`)
    - Runtime providers (`RDEPENDS*`/`RPROVIDES*`)
    - Related to the above: deptask, rdeptask, recrdeptask flags
    - Explicit task dependencies: depends flag

- Add a subcommand which emits information about all dependencies in both
  directions for any target
- Add additional types of variable filtering for `show`

    - Filter out 'unused' variables (not used by any tasks for the recipe)
    - Potentially improve flexibility regarding dependency traversal (e.g.
      show all dependencies rather than just function dependencies)

    - Consider adding an argument to disable variable expansion. This adds
      complexity but gives you output not unlike you would see in a config
      file or recipe, which has a certain amount of value, and file paths in
      particular are awfully unwieldy when expanded. A prototype of this is on
      a branch ('unexpanded')
    - Show variables with particular attributes / flags

        - Variables with documentation ('doc' flag)
        - Variables with defined variable types ('type' flag)
        - Variables with default values (??=, aka 'defaultval' flag)

- Add interactive mode

    - single up-front recipe parse step
    - add reparse subcommand

Code
----

- Restructure commands, move them out of 'bb'
- Move bb.main into the python package

Long term
---------

- Add a `bake` command, to do a build by spawning off a bitbake UI

Considering
-----------

- target vs pn arguments. In some cases, the user may be expecting that their
  input be interpreted more directly, as a recipe name, rather than as
  a target. For example, it could be unintuitive that 'bb showprovides eglibc'
  when using an external toolchain may actually show the provides of the
  external-sourcery-toolchain recipe.

    - For each command, determine which is most appropriate as a default
    - Consider adding an argument to change how the primary argument is
      interpreted
    - Add messages, possibly verbose/debug ones, which indicate how the input
      is mapped to an actual recipe file, or what recipe file is being used

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
