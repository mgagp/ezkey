/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * CLI Component: JSON Utilities
 * Description: JSON parsing, formatting and file handling utilities
 */

import * as fs from 'fs';
import * as path from 'path';

export class JsonUtils {
    /**
     * Pretty print JSON data
     */
    static prettyPrint(data: any, indent: number = 2): string {
        try {
            return JSON.stringify(data, null, indent);
        } catch (error) {
            return 'Error: Unable to serialize data to JSON';
        }
    }

    /**
     * Parse JSON data from string, with error handling
     */
    static parse(jsonString: string): any {
        try {
            return JSON.parse(jsonString);
        } catch (error) {
            throw new Error(`Invalid JSON: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Load JSON data from file
     * Supports both absolute and relative paths
     */
    static loadFromFile(filePath: string): any {
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
        } catch (error) {
            throw new Error(`Failed to load JSON from file ${filePath}: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Process input value - if it starts with @, treat as file path
     * Otherwise, try to parse as JSON string
     */
    static processInput(input: string): any {
        if (input.startsWith('@')) {
            // File input
            const filePath = input.substring(1);
            return this.loadFromFile(filePath);
        } else {
            // Direct JSON input
            return this.parse(input);
        }
    }

    /**
     * Format output based on pretty print setting
     */
    static formatOutput(data: any, prettyPrint: boolean = true): string {
        if (prettyPrint) {
            return this.prettyPrint(data);
        } else {
            return JSON.stringify(data);
        }
    }

    /**
     * Validate if string is valid JSON
     */
    static isValidJson(str: string): boolean {
        try {
            JSON.parse(str);
            return true;
        } catch {
            return false;
        }
    }

    /**
     * Safely extract value from nested object
     */
    static getValue(obj: any, path: string, defaultValue: any = null): any {
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