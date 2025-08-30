export declare class JsonUtils {
    /**
     * Pretty print JSON data
     */
    static prettyPrint(data: any, indent?: number): string;
    /**
     * Parse JSON data from string, with error handling
     */
    static parse(jsonString: string): any;
    /**
     * Load JSON data from file
     * Supports both absolute and relative paths
     */
    static loadFromFile(filePath: string): any;
    /**
     * Process input value - if it starts with @, treat as file path
     * Otherwise, try to parse as JSON string
     */
    static processInput(input: string): any;
    /**
     * Format output based on pretty print setting
     */
    static formatOutput(data: any, prettyPrint?: boolean): string;
    /**
     * Validate if string is valid JSON
     */
    static isValidJson(str: string): boolean;
    /**
     * Safely extract value from nested object
     */
    static getValue(obj: any, path: string, defaultValue?: any): any;
}
//# sourceMappingURL=json-utils.d.ts.map