package rules

import (
	"encoding/json"
	"fmt"
	"reflect"
	"regexp"
	"strings"
)

// Condition is a single field comparison.
type Condition struct {
	Field string `json:"field"`
	Op    string `json:"op"` // eq, neq, in, not_in, matches, contains, starts_with
	Value any    `json:"value"`
}

// ConditionGroup is a logical AND/OR group of conditions.
type ConditionGroup struct {
	All []Condition `json:"all,omitempty"`
	Any []Condition `json:"any,omitempty"`
}

// parseConditions converts a raw JSON blob into a map[string]any for storage.
func parseConditions(raw []byte) (map[string]any, error) {
	var m map[string]any
	if err := json.Unmarshal(raw, &m); err != nil {
		return nil, fmt.Errorf("unmarshal conditions: %w", err)
	}
	return m, nil
}

// decodeConditionGroup converts a map[string]any back into a ConditionGroup.
func decodeConditionGroup(m map[string]any, out *ConditionGroup) error {
	raw, err := json.Marshal(m)
	if err != nil {
		return fmt.Errorf("marshal conditions map: %w", err)
	}
	if err := json.Unmarshal(raw, out); err != nil {
		return fmt.Errorf("unmarshal condition group: %w", err)
	}
	return nil
}

// Evaluate returns true if the condition group matches the request.
func (g *ConditionGroup) Evaluate(req *EvalRequest) bool {
	// If both are empty → match everything.
	if len(g.All) == 0 && len(g.Any) == 0 {
		return true
	}

	// All conditions must match.
	for _, c := range g.All {
		if !c.matches(req) {
			return false
		}
	}

	// At least one of the Any conditions must match (if present).
	if len(g.Any) > 0 {
		anyMet := false
		for _, c := range g.Any {
			if c.matches(req) {
				anyMet = true
				break
			}
		}
		if !anyMet {
			return false
		}
	}

	return true
}

// matches checks a single Condition against the request.
func (c *Condition) matches(req *EvalRequest) bool {
	fieldVal := resolveField(c.Field, req)
	return applyOp(c.Op, fieldVal, c.Value)
}

// resolveField resolves a dot-path field from the request.
// Supported root fields: tool, input.*, machine.name, machine.type, session.agentType
func resolveField(field string, req *EvalRequest) any {
	parts := strings.SplitN(field, ".", 2)
	switch parts[0] {
	case "tool":
		return req.Tool
	case "input":
		if len(parts) == 2 {
			return dotGet(req.Input, parts[1])
		}
		return req.Input
	case "machine":
		if len(parts) == 2 {
			switch parts[1] {
			case "name":
				return req.MachineName
			case "type":
				return req.MachineType
			case "id":
				return req.MachineID
			}
		}
	case "session":
		if len(parts) == 2 && parts[1] == "agentType" {
			return req.AgentType
		}
	}
	return nil
}

// dotGet navigates a nested map using a dot-separated path.
func dotGet(m map[string]any, path string) any {
	parts := strings.SplitN(path, ".", 2)
	v, ok := m[parts[0]]
	if !ok {
		return nil
	}
	if len(parts) == 1 {
		return v
	}
	nested, ok := v.(map[string]any)
	if !ok {
		return nil
	}
	return dotGet(nested, parts[1])
}

func applyOp(op string, fieldVal, condVal any) bool {
	fieldStr := fmt.Sprintf("%v", fieldVal)

	switch op {
	case "eq":
		return reflect.DeepEqual(fieldVal, condVal) || fieldStr == fmt.Sprintf("%v", condVal)
	case "neq":
		return !reflect.DeepEqual(fieldVal, condVal) && fieldStr != fmt.Sprintf("%v", condVal)
	case "in":
		return inList(fieldVal, condVal)
	case "not_in":
		return !inList(fieldVal, condVal)
	case "matches":
		pattern, ok := condVal.(string)
		if !ok {
			return false
		}
		re, err := regexp.Compile(pattern)
		if err != nil {
			return false
		}
		return re.MatchString(fieldStr)
	case "contains":
		condStr, ok := condVal.(string)
		if !ok {
			return false
		}
		return strings.Contains(fieldStr, condStr)
	case "starts_with":
		condStr, ok := condVal.(string)
		if !ok {
			return false
		}
		return strings.HasPrefix(fieldStr, condStr)
	}
	return false
}

func inList(val, list any) bool {
	fieldStr := fmt.Sprintf("%v", val)
	switch l := list.(type) {
	case []any:
		for _, item := range l {
			if reflect.DeepEqual(val, item) || fieldStr == fmt.Sprintf("%v", item) {
				return true
			}
		}
	case []string:
		for _, item := range l {
			if fieldStr == item {
				return true
			}
		}
	}
	return false
}
