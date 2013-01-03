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

import logging
import optparse
import os
import sys
import warnings
import bbtool.tinfoil
import bbtool.formatter
import bb
import bb.cache
import bb.codeparser


logger = logging.getLogger('bitbake-env')


def get_data(tinfoil, recipe=None):
    localdata = bb.data.createCopy(tinfoil.config_data)
    localdata.finalize()
    # TODO: why isn't expandKeys a method of DataSmart?
    bb.data.expandKeys(localdata)

    if recipe:
        taskdata = bb.taskdata.TaskData(abort=False)
        taskdata.add_provider(localdata, tinfoil.cooker.status, recipe)

        targetid = taskdata.getbuild_id(recipe)
        fnid = taskdata.build_targets[targetid][0]
        fn = taskdata.fn_index[fnid]

        try:
            envdata = bb.cache.Cache.loadDataFull(fn, tinfoil.cooker.get_file_appends(fn),
                                                  tinfoil.config_data)
        except Exception:
            logger.exception("Unable to read %s", fn)
            raise
        return envdata
    return localdata


# TODO: enhance bb.data.emit* to function more flexibly, like these
def escape_shell_value(value):
    value = value.replace('"', '\"')
    value = value.replace('`', '\`')
    return value


def format_variable(data, variable, flag=None, shell=False):
    if flag:
        unexpanded = data.getVarFlag(variable, flag, False)
        pattern = '%s[%s]=%%s' % (variable, flag)
    else:
        unexpanded = data.getVar(variable, False)
        pattern = '%s=%%s' % variable

    if data.getVarFlag(variable, 'unexport'):
        if flag:
            return pattern % unexpanded
        else:
            return '# ' + pattern % unexpanded

    try:
        expanded = bb.data.expand(unexpanded, data)
    except BaseException:
        if flag:
            logger.exception("Expansion of '%s[%s]' failed", variable, flag)
        else:
            logger.exception("Expansion of '%s' failed", variable)
        return '# ' + pattern % unexpanded
    else:
        message = ''
        if unexpanded != expanded:
            message += '# ' + pattern % unexpanded + '\n'

        if data.getVarFlag(variable, 'export'):
            message += 'export '

        if isinstance(expanded, basestring):
            expanded = '"%s"' % escape_shell_value(expanded)
        else:
            expanded = repr(expanded)
        message += pattern % expanded
        return message


ignored_flags = ('func', 'python', 'export', 'export_func')
def print_variable_flags(data, variable):
    flags = data.getVarFlags(variable)
    if not flags:
        return

    for flag, value in flags.iteritems():
        if flag.startswith('_') or flag in ignored_flags:
            continue
        value = str(value)
        print(format_variable(data, variable, flag))


def print_variable(data, variable):
    unexpanded = data.getVar(variable, False)
    if unexpanded is None:
        return
    unexpanded = str(unexpanded)


    flags = data.getVarFlags(variable) or {}
    if flags.get('func'):
        try:
            value = bb.data.expand(unexpanded, data)
        except BaseException:
            logger.exception("Expansion of '%s' failed", variable)
            return

        if flags.get('python'):
            # TODO: fix bitbake to show methodpool functions sanely like this
            if variable in bb.methodpool._parsed_fns:
                print(value)
            else:
                print("python %s () {\n%s}\n" % (variable, value))
        else:
            print("%s () {\n%s}\n" % (variable, value))
    else:
        print(format_variable(data, variable, shell=True))


def variable_function_deps(data, variable, deps, seen):
    variable_deps = deps and deps.get(variable) or set()
    if data.getVarFlag(variable, 'python'):
        # TODO: Fix generate_dependencies to return python function
        # execs dependencies, which seem to be missing for some reason
        parser = bb.codeparser.PythonParser(variable, logger)
        parser.parse_python(data.getVar(variable, False))
        variable_deps |= parser.execs
        deps[variable] = variable_deps

    for dep in variable_deps:
        if dep in seen:
            continue
        seen.add(dep)

        if data.getVarFlag(dep, 'func'):
            for _dep in variable_function_deps(data, dep, deps, seen):
                yield _dep
            yield dep

def dep_ordered_variables(data, variables, deps):
    seen = set()
    for variable in variables:
        if variable in seen:
            continue

        seen.add(variable)
        for dep in variable_function_deps(data, variable, deps, seen):
            yield dep
        yield variable

def sorted_variables(data, variables=None, show_deps=True):
    def key(v):
        # Order: unexported vars, exported vars, shell funcs, python funcs
        if data.getVarFlag(v, 'func'):
            return int(bool(data.getVarFlag(v, 'python'))) + 2
        else:
            return int(bool(data.getVarFlag(v, 'export')))

    all_variables = data.keys()
    if not variables:
        variables = sorted(all_variables, key=lambda v: v.lower())
        variables = filter(lambda v: not v.startswith('_'), variables)
    else:
        for variable in variables:
            if variable not in all_variables:
                logger.warn("Requested variable '%s' does not exist", variable)
        variables = sorted(variables, key=lambda v: v.lower())
        if show_deps:
            deps = bb.data.generate_dependencies(data)[1]
            variables = list(dep_ordered_variables(data, variables, deps))

    variables = sorted(variables, key=key)
    return variables

def show(args):
    log_format = bbtool.formatter.Formatter("%(levelname)s: %(message)s")
    if sys.stderr.isatty():
        log_format.enable_color()
    console = logging.StreamHandler(sys.stderr)
    console.setFormatter(log_format)
    console.setLevel(logging.INFO)
    logger.addHandler(console)

    tinfoil = bbtool.tinfoil.Tinfoil(output=sys.stderr)
    tinfoil.prepare(config_only=True)

    ignore = tinfoil.config_data.getVar("ASSUME_PROVIDED", True) or ""
    if args.recipe and args.recipe in ignore.split():
        logger.warn("%s is in ASSUME_PROVIDED" % args.recipe)

    # Let us show the recipe even if it is in ASSUME_PROVIDED
    # TODO: let bitbake -e support showing assumed recipes, the way this does
    tinfoil.config_data.setVar("ASSUME_PROVIDED", "")

    if args.recipe:
        tinfoil.parseRecipes()

    if args.recipe:
        data = get_data(tinfoil, args.recipe)
    else:
        data = get_data(tinfoil)

    variables = sorted_variables(data, args.variables, args.dependencies)
    for variable in variables:
        print_variable(data, variable)
        if args.flags:
            print_variable_flags(data, variable)
