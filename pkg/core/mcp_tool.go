package core

// MCPToolType represents the type of MCP tool
type MCPToolType int64

const (
	MCP_TOOL_TYPE_QUERY_ALL_ACCOUNTS               MCPToolType = 1
	MCP_TOOL_TYPE_QUERY_ALL_ACCOUNTS_BALANCE       MCPToolType = 2
	MCP_TOOL_TYPE_QUERY_ALL_TRANSACTION_CATEGORIES MCPToolType = 3
	MCP_TOOL_TYPE_QUERY_ALL_TRANSACTION_TAGS       MCPToolType = 4
	MCP_TOOL_TYPE_QUERY_LATEST_EXCHANGE_RATES      MCPToolType = 5
	MCP_TOOL_TYPE_QUERY_TRANSACTIONS               MCPToolType = 6
	MCP_TOOL_TYPE_ADD_TRANSACTION                  MCPToolType = 7
	MCP_TOOL_TYPE_DELETE_TRANSACTION               MCPToolType = 8

	// Finexy tools use bits from 40 so tools added upstream cannot take the same permanent bit.
	MCP_TOOL_TYPE_QUERY_IMPORT_CONTEXT     MCPToolType = 40
	MCP_TOOL_TYPE_QUERY_MANAGEMENT_CONTEXT MCPToolType = 41
	MCP_TOOL_TYPE_PREVIEW_BILL_IMPORT      MCPToolType = 42
	MCP_TOOL_TYPE_MAP_BILL_IMPORT          MCPToolType = 43
	MCP_TOOL_TYPE_CONFIRM_BILL_IMPORT      MCPToolType = 44
	MCP_TOOL_TYPE_CREATE_ACCOUNT           MCPToolType = 45
	MCP_TOOL_TYPE_UPDATE_ACCOUNT           MCPToolType = 46
	MCP_TOOL_TYPE_SET_ACCOUNT_HIDDEN       MCPToolType = 47
	MCP_TOOL_TYPE_DELETE_ACCOUNT           MCPToolType = 48
	MCP_TOOL_TYPE_CREATE_CATEGORY          MCPToolType = 49
	MCP_TOOL_TYPE_UPDATE_CATEGORY          MCPToolType = 50
	MCP_TOOL_TYPE_SET_CATEGORY_HIDDEN      MCPToolType = 51
	MCP_TOOL_TYPE_DELETE_CATEGORY          MCPToolType = 52

	MCP_TOOL_MAX_TYPE_VALUE = MCP_TOOL_TYPE_DELETE_TRANSACTION

	mcpFinexyToolMinType = MCP_TOOL_TYPE_QUERY_IMPORT_CONTEXT
	mcpFinexyToolMaxType = MCP_TOOL_TYPE_DELETE_CATEGORY

	// MCPAllTools is the mask of all currently supported tools
	MCPAllTools int64 = (1<<MCP_TOOL_MAX_TYPE_VALUE - 1) | (1<<mcpFinexyToolMaxType-1)&^(1<<(mcpFinexyToolMinType-1)-1)
)

// IsEnabled returns whether the tool is enabled in the given permission mask
func (t MCPToolType) IsEnabled(mask int64) bool {
	return t >= 1 && mask >= 0 && mask&(int64(1)<<(t-1)) != 0
}
