#!/usr/bin/env python
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License version 2 as
# published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along
# with this program; if not, write to the Free Software Foundation, Inc.,
# 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.


import argparse
import bbtool.formatter
import bbtool.show
import bbtool.tinfoil
import logging
import sys

PATH = os.getenv('PATH').split(':')
bitbake_paths = [os.path.join(path, '..', 'lib')
                 for path in PATH if os.path.exists(os.path.join(path, 'bitbake'))]
if not bitbake_paths:
    sys.exit("Unable to locate bitbake, please ensure PATH is set correctly.")

sys.path[0:0] = bitbake_paths

import bb.msg


def arg(*args, **kw):
    def f(fn):
        if not hasattr(fn, 'args'):
            fn.args = []
        fn.args.insert(0, (args, kw))
        return fn
    return f


class Commands(object):
    def __init__(self):
        self.commands = self.get_commands()
        self.parser = argparse.ArgumentParser(description=self.__doc__)
        self.subparsers = self.parser.add_subparsers()
        for command in self.commands:
            if hasattr(command, 'help'):
                help = command.help
            else:
                if command.__doc__ and '\n' not in command.__doc__:
                    help = command.__doc__
                else:
                    help = None

            parser = self.subparsers.add_parser(command.__name__[3:], description=command.__doc__, help=help)
            if hasattr(command, 'args'):
                for args, kw in command.args:
                    parser.add_argument(*args, **kw)
            parser.set_defaults(func=command)

    def parse_args(self, args):
        try:
            args = self.parser.parse_args(args)
        except argparse.ArgumentError, exc:
            print(exc)
            raise
        args.func(args)

    def get_commands(self):
        commands = []
        for attr in dir(self):
            if attr.startswith('do_'):
                commands.append(getattr(self, attr))
        return commands


def prepare_taskdata(providers):
    # Silence messages about missing/unbuildable, as we don't care
    bb.taskdata.logger.setLevel(logging.CRITICAL)

    tinfoil = bbtool.tinfoil.Tinfoil(output=sys.stderr)
    tinfoil.prepare()

    localdata = bb.data.createCopy(tinfoil.config_data)
    localdata.finalize()
    # TODO: why isn't expandKeys a method of DataSmart?
    bb.data.expandKeys(localdata)

    taskdata = bb.taskdata.TaskData(abort=False)

    if 'world' in providers:
        tinfoil.cooker.buildWorldTargetList()
        providers.remove('world')
        providers.extend(tinfoil.cooker.status.world_target)

    if 'universe' in providers:
        providers.remove('universe')
        providers.extend(tinfoil.cooker.status.universe_target)

    for provider in providers:
        taskdata.add_provider(localdata, tinfoil.cooker.status, provider)

    taskdata.add_unresolved(localdata, tinfoil.cooker.status)

    return tinfoil, taskdata


class BitBakeCommands(Commands):
    "Prototype subcommand-based bitbake UI"

    logger = logging.getLogger('bb')

    @arg('-d', '--dependencies', action='store_true', help='follow variable dependencies (functions only, if showing expanded)')
    @arg('-u', '--unexpanded', action='store_true', help="don't expand variables")
    @arg('-f', '--flags', action='store_true', help='also show flags')
    @arg('-r', '--recipe', help='operate against this recipe')
    @arg('variables', nargs='*', help='variables to show (default: all variables)')
    def do_show(self, args):
        """Show bitbake metadata (global or recipe)"""
        bbtool.show.show(args)

    @arg('target')
    @arg('recipes', default=['universe'], nargs='*',
         help='recipes to check for dependencies on target (default: universe)')
    def do_whatdepends(self, args):
        """Show what depends on the specified target"""

        providers = list(args.recipes)
        providers.append(args.target)
        tinfoil, taskdata = prepare_taskdata(providers)

        targetid = taskdata.getbuild_id(args.target)
        fnid = taskdata.build_targets[targetid][0]
        dep_fnids = taskdata.get_dependees(targetid)
        for dep_fnid in dep_fnids:
            for target in taskdata.build_targets:
                if dep_fnid in taskdata.build_targets[target]:
                    print(taskdata.build_names_index[target])

    @arg('target')
    def do_showdepends(self, args):
        """Show what the specified target depends upon"""

        tinfoil, taskdata = prepare_taskdata([args.target])

        targetid = taskdata.getbuild_id(args.target)
        fnid = taskdata.build_targets[targetid][0]
        for dep in taskdata.depids[fnid]:
            print(taskdata.build_names_index[dep])

    @arg('target')
    def do_whatprovides(self, args):
        """Show what recipes provide the specified target"""

        tinfoil, taskdata = prepare_taskdata([args.target])

        targetid = taskdata.getbuild_id(args.target)
        first_provider = taskdata.build_targets[targetid][0]
        print(taskdata.fn_index[first_provider] + '*')
        for fnid in taskdata.build_targets[targetid][1:]:
            print(taskdata.fn_index[fnid])

    @arg('command', help='show help for this subcommand', nargs='?')
    def do_help(self, args):
        """Show overall help, or help for a specific subcommand"""

        if not args.command:
            self.parser.print_help()
        elif args.command not in self.subparsers._name_parser_map:
            self.parser.exit("Invalid command '%s'" % args.command)
        else:
            self.subparsers._name_parser_map[args.command].print_help()


if __name__ == '__main__':
    log_format = bbtool.formatter.Formatter("%(levelname)s: %(message)s")
    if sys.stderr.isatty():
        log_format.enable_color()
    console = logging.StreamHandler(sys.stderr)
    console.setFormatter(log_format)

    c = BitBakeCommands()
    c.logger.addHandler(console)
    c.logger.setLevel(logging.INFO)

    if len(sys.argv) == 1:
        c.parser.print_help()
        sys.exit(2)
    c.parse_args(sys.argv[1:])
