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

    $ bb show -r bash FILE
    Parsing recipes..done.
    FILE="/scratch/mel7/bb/poky/meta/recipes-extended/bash/bash_4.2.bb"

    $ bb whatprovides bash
    Parsing recipes..done.
    /scratch/mel7/bb/poky/meta/recipes-extended/bash/bash_4.2.bb*
