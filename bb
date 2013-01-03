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


class Terminate(BaseException):
    pass


def sigterm_exception(signum, stackframe):
    raise Terminate()


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


class BitBakeCommands(Commands):
    "Prototype subcommand-based bitbake UI"

    logger = logging.getLogger('bb')


    def prepare_taskdata(self, provided=None, rprovided=None):
        # Silence messages about missing/unbuildable, as we don't care
        bb.taskdata.logger.setLevel(logging.CRITICAL)

        self.tinfoil = bbtool.tinfoil.Tinfoil(output=sys.stderr)
        self.tinfoil.prepare()

        self.cooker = self.tinfoil.cooker
        self.cache_data = self.tinfoil.cooker.status

        self.localdata = bb.data.createCopy(self.tinfoil.config_data)
        self.localdata.finalize()
        # TODO: why isn't expandKeys a method of DataSmart?
        bb.data.expandKeys(self.localdata)

        self.taskdata = bb.taskdata.TaskData(abort=False)

        if provided:
            self.add_provided(provided)

        if rprovided:
            self.add_rprovided(provided)

    def add_rprovided(self, rprovided):
        for item in rprovided:
            self.taskdata.add_rprovider(self.localdata, self.cache_data, item)

        self.taskdata.add_unresolved(self.localdata, self.cache_data)

    def add_provided(self, provided):
        if 'world' in provided:
            if not self.cache_data.world_target:
                self.cooker.buildWorldTargetList()
            provided.remove('world')
            provided.extend(self.cache_data.world_target)

        if 'universe' in provided:
            provided.remove('universe')
            provided.extend(self.cache_data.universe_target)

        for item in provided:
            self.taskdata.add_provider(self.localdata, self.cache_data, item)

        self.taskdata.add_unresolved(self.localdata, self.cache_data)

    def rec_get_dependees(self, targetid, depth=0):
        for dependee_fnid, dependee_id in self.get_dependees(targetid):
            yield dependee_id, depth

            for _id, _depth in self.rec_get_dependees(dependee_id, depth+1):
                yield _id, _depth

    def get_dependees(self, targetid):
        fnid = self.taskdata.build_targets[targetid][0]
        dep_fnids = self.taskdata.get_dependees(targetid)
        for dep_fnid in dep_fnids:
            for target in self.taskdata.build_targets:
                if dep_fnid in self.taskdata.build_targets[target]:
                    yield dep_fnid, target

    @arg('-d', '--dependencies', action='store_true',
         help='also show functions the vars depend on')
    @arg('-f', '--flags', action='store_true', help='also show flags')
    @arg('-r', '--recipe', help='operate against this recipe')
    @arg('variables', nargs='*', help='variables to show (default: all variables)')
    def do_show(self, args):
        """Show bitbake metadata (global or recipe)"""
        bbtool.show.show(args)

    @arg('-r', '--recursive', action='store_true',
         help='operate recursively, with indent reflecting depth')
    @arg('target')
    @arg('recipes', default=['universe'], nargs='*',
         help='recipes to check for dependencies on target (default: universe)')
    def do_whatdepends(self, args):
        """Show what depends on the specified target"""

        providers = list(args.recipes)
        providers.append(args.target)
        self.prepare_taskdata(providers)

        targetid = self.taskdata.getbuild_id(args.target)
        if args.recursive:
            for dep_id, depth in self.rec_get_dependees(targetid):
                print('  '*depth + self.taskdata.build_names_index[dep_id])
        else:
            for dep_fnid, dep_id in self.get_dependees(targetid):
                print(self.taskdata.build_names_index[dep_id])

    @arg('target')
    def do_showdepends(self, args):
        """Show what the specified target depends upon"""

        self.prepare_taskdata([args.target])
        fnid = self.taskdata.get_provider(args.target)[0]
        fn = self.taskdata.fn_index[fnid]
        for dep in self.cache_data.deps[fn]:
            print(dep)

    @arg('target')
    def do_showprovides(self, args):
        """Show what the specified target provides"""

        self.prepare_taskdata([args.target])

        targetid = self.taskdata.getbuild_id(args.target)
        fnid = self.taskdata.build_targets[targetid][0]
        fn = self.taskdata.fn_index[fnid]

        for provide in sorted(self.cache_data.fn_provides[fn]):
            print(provide)

    @arg('target')
    def do_whatprovides(self, args):
        """Show what recipes provide the specified target"""

        self.prepare_taskdata([args.target])

        targetid = self.taskdata.getbuild_id(args.target)
        first_provider = self.taskdata.build_targets[targetid][0]
        print(self.taskdata.fn_index[first_provider] + '*')
        for fnid in self.taskdata.build_targets[targetid][1:]:
            print(self.taskdata.fn_index[fnid])

    @arg('target')
    def do_whatrprovides(self, args):
        """Show what recipes provide the specified target"""

        self.prepare_taskdata(rprovided=[args.target])

        targetid = self.taskdata.getrun_id(args.target)
        first_provider = self.taskdata.run_targets[targetid][0]
        print(self.taskdata.fn_index[first_provider] + '*')
        for fnid in self.taskdata.run_targets[targetid][1:]:
            print(self.taskdata.fn_index[fnid])

    @arg('command', help='show help for this subcommand', nargs='?')
    def do_help(self, args):
        """Show overall help, or help for a specific subcommand"""

        if not args.command:
            self.parser.print_help()
        elif args.command not in self.subparsers._name_parser_map:
            self.parser.exit("Invalid command '%s'" % args.command)
        else:
            self.subparsers._name_parser_map[args.command].print_help()


def main(arguments):
    log_format = bbtool.formatter.Formatter("%(levelname)s: %(message)s")
    if sys.stderr.isatty():
        log_format.enable_color()
    console = logging.StreamHandler(sys.stderr)
    console.setFormatter(log_format)

    c = BitBakeCommands()
    c.logger.addHandler(console)
    c.logger.setLevel(logging.INFO)

    if not arguments:
        c.parser.print_help()
        sys.exit(2)
    c.parse_args(arguments)


if __name__ == '__main__':
    import signal
    signal.signal(signal.SIGTERM, sigterm_exception)
    try:
        main(sys.argv[1:])
    except KeyboardInterrupt:
        signal.signal(signal.SIGINT, signal.SIG_DFL)
        os.kill(os.getpid(), signal.SIGINT)
    except Terminate:
        signal.signal(signal.SIGTERM, signal.SIG_DFL)
        os.kill(os.getpid(), signal.SIGTERM)
