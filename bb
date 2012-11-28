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
import bbcommands.formatter
import bbcommands.show
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
        fn.args.append((args, kw))
        return fn
    return f


class Commands(object):
    def __init__(self):
        self.commands = self.get_commands()
        self.parser = argparse.ArgumentParser(description=self.__doc__)
        self.subparsers = self.parser.add_subparsers()
        for command in self.commands:
            parser = self.subparsers.add_parser(command.__name__[3:], description=command.__doc__)
            if hasattr(command, 'args'):
                for args, kw in command.args:
                    parser.add_argument(*args, **kw)
            parser.set_defaults(func=command)

    def parse_args(self, args):
        args = self.parser.parse_args(args)
        args.func(args)

    def get_commands(self):
        commands = []
        for attr in dir(self):
            if attr.startswith('do_'):
                commands.append(getattr(self, attr))
        return commands


class BitBakeCommands(Commands):
    "Prototype subcommand-based bitbake UI"

    logger = logging.getLogger('bb')

    @arg('-d', '--dependencies', action='store_true', help='also show dependent functions')
    @arg('-f', '--flags', action='store_true', help='also show flags')
    @arg('-r', '--recipe', help='operate against this recipe')
    @arg('variables', nargs='*', help='variables to show')
    def do_show(self, args):
        """Show bitbake metadata (global or recipe)

        If no variables are specified, all will be shown."""
        import bbcommands.show
        bbcommands.show.show(args)

    @arg('target')
    def do_whatdepends(self, args):
        """Show what depends on the specified target"""
        import bbcommands.tinfoil

        # Silence messages about missing/unbuildable, as we don't care
        bb.taskdata.logger.setLevel(logging.CRITICAL)

        tinfoil = bbcommands.tinfoil.Tinfoil(output=sys.stderr)
        tinfoil.prepare()

        localdata = bb.data.createCopy(tinfoil.config_data)
        localdata.finalize()
        # TODO: why isn't expandKeys a method of DataSmart?
        bb.data.expandKeys(localdata)

        taskdata = bb.taskdata.TaskData(abort=False)
        taskdata.add_provider(localdata, tinfoil.cooker.status, args.target)

        tinfoil.cooker.buildWorldTargetList()
        for target in tinfoil.cooker.status.world_target:
            taskdata.add_provider(localdata, tinfoil.cooker.status, target)
        taskdata.add_unresolved(localdata, tinfoil.cooker.status)

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
        import bbcommands.tinfoil

        tinfoil = bbcommands.tinfoil.Tinfoil(output=sys.stderr)
        tinfoil.prepare()

        localdata = bb.data.createCopy(tinfoil.config_data)
        localdata.finalize()
        # TODO: why isn't expandKeys a method of DataSmart?
        bb.data.expandKeys(localdata)

        taskdata = bb.taskdata.TaskData(abort=False)
        taskdata.add_provider(localdata, tinfoil.cooker.status, args.target)
        targetid = taskdata.getbuild_id(args.target)
        fnid = taskdata.build_targets[targetid][0]
        for dep in taskdata.depids[fnid]:
            print(taskdata.build_names_index[dep])

    @arg('target')
    def do_whatprovides(self, args):
        """Show what recipes provide the specified target"""
        import bbcommands.tinfoil

        # Silence messages about missing/unbuildable, as we don't care
        bb.taskdata.logger.setLevel(logging.CRITICAL)

        tinfoil = bbcommands.tinfoil.Tinfoil(output=sys.stderr)
        tinfoil.prepare()

        localdata = bb.data.createCopy(tinfoil.config_data)
        localdata.finalize()
        # TODO: why isn't expandKeys a method of DataSmart?
        bb.data.expandKeys(localdata)

        taskdata = bb.taskdata.TaskData(abort=False)
        taskdata.add_provider(localdata, tinfoil.cooker.status, args.target)

        targetid = taskdata.getbuild_id(args.target)
        first_provider = taskdata.build_targets[targetid][0]
        print('*' + taskdata.fn_index[first_provider])
        for fnid in taskdata.build_targets[targetid][1:]:
            print(' ' + taskdata.fn_index[fnid])

    @arg('command', help='show help for this subcommand')
    def do_help(self, args):
        if args.command not in self.subparsers._name_parser_map:
            self.parser.exit("Invalid command '%s'" % args.command)
        else:
            self.subparsers._name_parser_map[args.command].print_help()


if __name__ == '__main__':
    log_format = bbcommands.formatter.Formatter("%(levelname)s: %(message)s")
    if sys.stderr.isatty():
        log_format.enable_color()
    console = logging.StreamHandler(sys.stderr)
    console.setFormatter(log_format)

    c = BitBakeCommands()
    c.logger.addHandler(console)
    c.logger.setLevel(logging.INFO)
    c.parse_args(sys.argv[1:])
