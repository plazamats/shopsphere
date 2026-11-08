package com.shopsphere.client.ui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** A read-only table of objects. Columns are a title plus a function that reads the cell from the object. */
public class DataTable<T> extends JScrollPane {

    private static final long serialVersionUID = 1L;

    public record Column<T>(String title, Function<T, Object> value) { }

    private final transient List<Column<T>> columns;
    private final transient List<T> rows = new ArrayList<>();
    private final JTable table;
    private final transient AbstractTableModel model;

    public DataTable(List<Column<T>> columns) {
        this.columns = columns;
        this.model = new AbstractTableModel() {
            private static final long serialVersionUID = 1L;
            @Override public int getRowCount() { return rows.size(); }
            @Override public int getColumnCount() { return columns.size(); }
            @Override public String getColumnName(int c) { return columns.get(c).title(); }
            @Override public Object getValueAt(int r, int c) { return columns.get(c).value().apply(rows.get(r)); }
        };
        this.table = new JTable(model);
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setGridColor(Theme.BORDER);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(new Color(0xE0E7FF));
        table.setSelectionForeground(Theme.TEXT);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(new Color(0xF9FAFB));
        table.getTableHeader().setForeground(Theme.MUTED);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, false, r, c);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                if (!sel) setBackground(r % 2 == 0 ? Color.WHITE : new Color(0xFAFAFB));
                return this;
            }
        });
        setViewportView(table);
        setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        getViewport().setBackground(Color.WHITE);
    }

    public void setRows(List<T> newRows) {
        rows.clear();
        rows.addAll(newRows);
        model.fireTableDataChanged();
    }

    /** The selected object, or null when nothing is selected. */
    public T selected() {
        int i = table.getSelectedRow();
        return i < 0 ? null : rows.get(i);
    }

    public JTable table() {
        return table;
    }

    public int columnCount() {
        return columns.size();
    }
}
