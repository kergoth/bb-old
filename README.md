bb
==

Experimentation with a subcommand-based bitbake UI (initially just inspection tools)

Known Issues
------------

- Provider and pn aren't always correctly mapped back and forth


Some Examples
-------------

    # Determine what pulls ncurses into a build of core-image-minimal
    $ bb whatdepends ncurses core-image-minimal
    Parsing recipes..done.
    virtual/gettext
    virtual/libintl
    readline
    util-linux
    sqlite3
    attr

    $ bb whatdepends -r attr core-image-minimal
    Parsing recipes..done.
    acl
    libcap
      avahi
      libgcrypt
        gtk+
          libglade

    $ bb showdepends bash
    Parsing recipes..done.
    autoconf-native
    automake-native
    libtool-native
    libtool-cross
    gnu-config-native
    virtual/gettext
    gettext-native
    virtual/i686-pc-linux-gnu-gcc
    virtual/i686-pc-linux-gnu-compilerlibs
    virtual/libc
    ncurses
    bison-native

    $ bb whatrprovides update-alternatives
    /scratch/mel7/bb/poky/meta/recipes-devtools/opkg/opkg_svn.bb*
    /scratch/mel7/bb/poky/meta/recipes-devtools/dpkg/dpkg_1.16.9.bb

    $ bb whatprovides bash
    Parsing recipes..done.
    /scratch/mel7/bb/poky/meta/recipes-extended/bash/bash_4.2.bb*
    
    $ bb show DISTRO MACHINE TUNE_ARCH TUNE_FEATURES
    # DISTRO="mel"
    # MACHINE="p4080ds"
    # TUNE_ARCH="${@bb.utils.contains("TUNE_FEATURES", "m32", "powerpc", "", d)}"
    TUNE_ARCH="powerpc"
    # TUNE_FEATURES="${TUNE_FEATURES_tune-${DEFAULTTUNE}}"
    TUNE_FEATURES="m32 fpu-hard ppce500mc"
    
    $ bb show -d -f COPYLEFT_RECIPE_TYPE
    # COPYLEFT_RECIPE_TYPE="${@copyleft_recipe_type(d)}"
    COPYLEFT_RECIPE_TYPE="target"
    COPYLEFT_RECIPE_TYPE[doc]="The "type" of the current recipe (e.g. target, native, cross)"
    def copyleft_recipe_type(d):
        for recipe_type in oe.data.typed_value('COPYLEFT_AVAILABLE_RECIPE_TYPES', d):
            if oe.utils.inherits(d, recipe_type):
                return recipe_type
        return 'target'
    
    $ bb show -r virtual/kernel PROVIDES
    Parsing recipes..done.
    PROVIDES="linux-qoriq-sdk-3.0.34 linux-qoriq-sdk-3.0.34-r9b linux-qoriq-sdk  virtual/kernel"
