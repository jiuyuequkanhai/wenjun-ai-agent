package com.yupi.yuaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalOperationToolTest {

    @Test
    void executeTerminalCommand() {
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        String command = System.getProperty("os.name").toLowerCase().contains("win")
                ? "echo terminal-tool-ok"
                : "printf terminal-tool-ok";
        String result = terminalOperationTool.executeTerminalCommand(command);
        assertTrue(result.contains("terminal-tool-ok"), result);
    }
}
