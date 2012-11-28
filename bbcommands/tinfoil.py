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

import bbcommands.formatter
import bb.providers
import bb.tinfoil
import logging
import os
import sys
import warnings

# TODO: Let bb.tinfoil.Tinfoil support output files other than stdout, and to
# enable color support in the formatter when it's a tty.
class Tinfoil(bb.tinfoil.Tinfoil):
    def __init__(self, output=sys.stdout):
        # Needed to avoid deprecation warnings with python 2.6
        warnings.filterwarnings("ignore", category=DeprecationWarning)

        # Set up logging
        self.logger = logging.getLogger('BitBake')
        console = logging.StreamHandler(output)
        format = bbcommands.formatter.Formatter("%(levelname)s: %(message)s", output=output)
        if output.isatty():
            format.enable_color()
        bb.msg.addDefaultlogFilter(console)
        console.setFormatter(format)
        self.logger.addHandler(console)

        initialenv = os.environ.copy()
        bb.utils.clean_environment()
        self.config = bb.tinfoil.TinfoilConfig(parse_only=True)
        self.cooker = bb.cooker.BBCooker(self.config, self.register_idle_function,
                                         initialenv)
        self.config_data = self.cooker.configuration.data
        bb.providers.logger.setLevel(logging.ERROR)
        self.cooker_data = None

