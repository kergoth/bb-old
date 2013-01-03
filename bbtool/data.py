import bb.data_smart
import oe.types

class Data(bb.data_smart.DataSmart):
    def __init__(self, parent):
        if parent:
            super(Data, self).__init__(seen=parent._seen_overrides.copy(),
                                       special=parent._special_values.copy())
            self.dict["_data"] = parent.dict
        else:
            super(Data, self).__init__()

    def copy(self):
        return Data(self)

    def createCopy(self):
        return self.copy()

    def inherits(self, *classes):
        inherited = self['__inherit_cache'] or []
        return any(('classes/%s.bbclass' % cls) in inherited
                   for cls in classes)

    def get(self, variable, default=None):
        value = self.getVar(variable)
        if value is None:
            return default
        else:
            return value

    def get_flag(self, variable, flag, default=None):
        value = self.getVarFlag(variable, flag, expand=True)
        if value is None:
            return default
        else:
            return value

    def get_flags(self, variable):
        return self.getVarFlags(variable)

    def update_flags(self, variable, dictvalues=None, **values):
        if dictvalues:
            values.update(dictvalues)

        for flag, value in values.iteritems():
            self.setVarFlag(variable, flag, value)

    def setdefault(self, variable, default=None):
        value = self.getVar(variable)
        if value is None:
            self.setVar(variable, default)
            return default
        else:
            return value

    def getVar(self, variable, expand=True):
        if expand:
            return oe.types.value(variable, self)
        else:
            return super(Data, self).getVar(variable, expand)

    def setVars(self, dictvalues=None, **values):
        if dictvalues:
            values.update(dictvalues)

        for variable, value in values.iteritems():
            self.setVar(variable, value)

    def setVarFlags(self, variable, values=None, **flags):
        if values:
            flags.update(values)

        for flag, value in flags.iteritems():
            self.setVarFlag(variable, flag, value)

    def __getitem__(self, variable):
        value = self.getVar(variable)
        if value is None:
            raise KeyError(variable)
        else:
            return value
