"use strict";
/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: JSON Utilities
 * Description: JSON parsing, formatting and file handling utilities
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.JsonUtils = void 0;
const fs = __importStar(require("fs"));
const path = __importStar(require("path"));
class JsonUtils {
    /**
     * Pretty print JSON data
     */
    static prettyPrint(data, indent = 2) {
        try {
            return JSON.stringify(data, null, indent);
        }
        catch (error) {
            return 'Error: Unable to serialize data to JSON';
        }
    }
    /**
     * Parse JSON data from string, with error handling
     */
    static parse(jsonString) {
        try {
            return JSON.parse(jsonString);
        }
        catch (error) {
            throw new Error(`Invalid JSON: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
    /**
     * Load JSON data from file
     * Supports both absolute and relative paths
     */
    static loadFromFile(filePath) {
        try {
            // Resolve relative paths
            const resolvedPath = path.isAbsolute(filePath)
                ? filePath
                : path.resolve(process.cwd(), filePath);
            if (!fs.existsSync(resolvedPath)) {
                throw new Error(`File not found: ${resolvedPath}`);
            }
            const content = fs.readFileSync(resolvedPath, 'utf-8');
            return this.parse(content);
        }
        catch (error) {
            throw new Error(`Failed to load JSON from file ${filePath}: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
    /**
     * Process input value - if it starts with @, treat as file path
     * Otherwise, try to parse as JSON string
     */
    static processInput(input) {
        if (input.startsWith('@')) {
            // File input
            const filePath = input.substring(1);
            return this.loadFromFile(filePath);
        }
        else {
            // Direct JSON input
            return this.parse(input);
        }
    }
    /**
     * Format output based on pretty print setting
     */
    static formatOutput(data, prettyPrint = true) {
        if (prettyPrint) {
            return this.prettyPrint(data);
        }
        else {
            return JSON.stringify(data);
        }
    }
    /**
     * Validate if string is valid JSON
     */
    static isValidJson(str) {
        try {
            JSON.parse(str);
            return true;
        }
        catch {
            return false;
        }
    }
    /**
     * Safely extract value from nested object
     */
    static getValue(obj, path, defaultValue = null) {
        const keys = path.split('.');
        let current = obj;
        for (const key of keys) {
            if (current === null || current === undefined || !(key in current)) {
                return defaultValue;
            }
            current = current[key];
        }
        return current;
    }
}
exports.JsonUtils = JsonUtils;
//# sourceMappingURL=json-utils.js.map