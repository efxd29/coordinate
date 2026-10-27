package dev.ben.phantomkiller;

import android.os.Bundle;

interface ICommandService {
    void destroy() = 16777114;
    Bundle execute(in String[] command) = 1;
}
