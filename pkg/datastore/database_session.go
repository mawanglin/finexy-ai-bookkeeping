package datastore

import (
	"database/sql"

	"xorm.io/xorm"
)

// DatabaseSession represents a database query or transaction session
type DatabaseSession interface {
	// Table sets the table for the query
	Table(tableNameOrBean any) DatabaseSession

	// Select sets the selected columns or expression
	Select(str string) DatabaseSession

	// Insert inserts one or more records into the database
	Insert(beans ...any) (int64, error)

	// Update updates one or more records in the database
	Update(bean any, condiBean ...any) (int64, error)

	// Delete deletes one or more records from the database
	Delete(beans ...any) (int64, error)

	// Get retrieves one record from the database
	Get(beans ...any) (bool, error)

	// Find retrieves multiple records from the database
	Find(rowsSlicePtr any, condiBean ...any) error

	// Count counts the number of records in the database
	Count(bean ...any) (int64, error)

	// Exist checks if a record exists in the database
	Exist(bean ...any) (bool, error)

	// Exec executes a raw SQL statement in the database
	Exec(sqlOrArgs ...any) (sql.Result, error)

	// Where sets the query condition
	Where(query any, args ...any) DatabaseSession

	// ID sets the primary key condition
	ID(id any) DatabaseSession

	// Cols sets the columns for the query
	Cols(columns ...string) DatabaseSession

	// GroupBy sets the grouping columns
	GroupBy(keys string) DatabaseSession

	// Having sets the condition for grouped results
	Having(conditions string) DatabaseSession

	// OrderBy sets the ordering for the query
	OrderBy(order any, args ...any) DatabaseSession

	// Limit sets the maximum number of results and optional offset
	Limit(limit int, start ...int) DatabaseSession

	// SetExpr sets an expression for the specified column
	SetExpr(column string, expression any) DatabaseSession

	// And appends a query condition
	And(query any, args ...any) DatabaseSession

	// In sets the query condition for values in the specified column
	In(column string, args ...any) DatabaseSession

	// NotIn sets the query condition for values not in the specified column
	NotIn(column string, args ...any) DatabaseSession

	// Join joins another table to the query
	Join(joinOperator string, tableName any, condition any, args ...any) DatabaseSession

	// Distinct selects distinct values of the specified columns
	Distinct(columns ...string) DatabaseSession

	// Asc sets ascending ordering for the specified columns
	Asc(columns ...string) DatabaseSession

	// Desc sets descending ordering for the specified columns
	Desc(columns ...string) DatabaseSession

	// QuoteIdentifier quotes an identifier for the current database dialect
	QuoteIdentifier(name string) string

	// TableName returns the mapped table name of the specified model
	TableName(bean any) string

	// Close releases the session and its underlying connection
	Close() error
}

// XOrmSession represents a database session using xorm
type XOrmSession struct {
	*xorm.Session
}

// Table sets the table for the query
func (s *XOrmSession) Table(tableNameOrBean any) DatabaseSession {
	s.Session.Table(tableNameOrBean)
	return s
}

// Select sets the selected columns or expression
func (s *XOrmSession) Select(str string) DatabaseSession {
	s.Session.Select(str)
	return s
}

// Insert inserts one or more records into the database
func (s *XOrmSession) Insert(beans ...any) (int64, error) {
	return s.Session.Insert(beans...)
}

// Update updates one or more records in the database
func (s *XOrmSession) Update(bean any, condiBean ...any) (int64, error) {
	return s.Session.Update(bean, condiBean...)
}

// Delete deletes one or more records from the database
func (s *XOrmSession) Delete(beans ...any) (int64, error) {
	return s.Session.Delete(beans...)
}

// Get retrieves one record from the database
func (s *XOrmSession) Get(beans ...any) (bool, error) {
	return s.Session.Get(beans...)
}

// Find retrieves multiple records from the database
func (s *XOrmSession) Find(rowsSlicePtr any, condiBean ...any) error {
	return s.Session.Find(rowsSlicePtr, condiBean...)
}

// Count counts the number of records in the database
func (s *XOrmSession) Count(bean ...any) (int64, error) {
	return s.Session.Count(bean...)
}

// Exist checks if a record exists in the database
func (s *XOrmSession) Exist(bean ...any) (bool, error) {
	return s.Session.Exist(bean...)
}

// Exec executes a raw SQL statement in the database
func (s *XOrmSession) Exec(sqlOrArgs ...any) (sql.Result, error) {
	return s.Session.Exec(sqlOrArgs...)
}

// Where sets the query condition
func (s *XOrmSession) Where(query any, args ...any) DatabaseSession {
	s.Session.Where(query, args...)
	return s
}

// ID sets the primary key condition
func (s *XOrmSession) ID(id any) DatabaseSession {
	s.Session.ID(id)
	return s
}

// Cols sets the columns for the query
func (s *XOrmSession) Cols(columns ...string) DatabaseSession {
	s.Session.Cols(columns...)
	return s
}

// GroupBy sets the grouping columns
func (s *XOrmSession) GroupBy(keys string) DatabaseSession {
	s.Session.GroupBy(keys)
	return s
}

// Having sets the condition for grouped results
func (s *XOrmSession) Having(conditions string) DatabaseSession {
	s.Session.Having(conditions)
	return s
}

// Join joins another table to the query
func (s *XOrmSession) Join(joinOperator string, tableName any, condition any, args ...any) DatabaseSession {
	s.Session.Join(joinOperator, tableName, condition, args...)
	return s
}

// OrderBy sets the ordering for the query
func (s *XOrmSession) OrderBy(order any, args ...any) DatabaseSession {
	s.Session.OrderBy(order, args...)
	return s
}

// Limit sets the maximum number of results and optional offset
func (s *XOrmSession) Limit(limit int, start ...int) DatabaseSession {
	s.Session.Limit(limit, start...)
	return s
}

// SetExpr sets an expression for the specified column
func (s *XOrmSession) SetExpr(column string, expression any) DatabaseSession {
	s.Session.SetExpr(column, expression)
	return s
}

// And appends a query condition
func (s *XOrmSession) And(query any, args ...any) DatabaseSession {
	s.Session.And(query, args...)
	return s
}

// In sets the query condition for values in the specified column
func (s *XOrmSession) In(column string, args ...any) DatabaseSession {
	s.Session.In(column, args...)
	return s
}

// NotIn sets the query condition for values not in the specified column
func (s *XOrmSession) NotIn(column string, args ...any) DatabaseSession {
	s.Session.NotIn(column, args...)
	return s
}

// Distinct selects distinct values of the specified columns
func (s *XOrmSession) Distinct(columns ...string) DatabaseSession {
	s.Session.Distinct(columns...)
	return s
}

// Asc sets ascending ordering for the specified columns
func (s *XOrmSession) Asc(columns ...string) DatabaseSession {
	s.Session.Asc(columns...)
	return s
}

// Desc sets descending ordering for the specified columns
func (s *XOrmSession) Desc(columns ...string) DatabaseSession {
	s.Session.Desc(columns...)
	return s
}

// QuoteIdentifier quotes an identifier for the current database dialect
func (s *XOrmSession) QuoteIdentifier(name string) string {
	return s.Session.Engine().Quote(name)
}

// TableName returns the mapped table name of the specified model
func (s *XOrmSession) TableName(bean any) string {
	return s.Session.Engine().TableName(bean)
}
