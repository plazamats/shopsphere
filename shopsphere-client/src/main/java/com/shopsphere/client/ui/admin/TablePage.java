package com.shopsphere.client.ui.admin;

import com.shopsphere.client.ui.DataTable;
import com.shopsphere.client.ui.Refreshable;
import com.shopsphere.client.ui.Ui;
import com.shopsphere.client.util.Async;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JPanel;

/** A heading, a table and a row of buttons. Most administration screens are this shape. */
abstract class TablePage<T> extends JPanel implements Refreshable {

    private static final long serialVersionUID = 1L;

    protected final transient DataTable<T> table;
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));

    protected TablePage(String title, String subtitle, List<DataTable.Column<T>> columns) {
        super(new BorderLayout());
        table = new DataTable<>(columns);
        JPanel page = Ui.page(title, subtitle);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        top.setOpaque(false);
        actions.setOpaque(false);
        body.add(top, BorderLayout.NORTH);
        body.add(table, BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);
        page.add(body, BorderLayout.CENTER);
        add(page);
    }

    protected JButton action(String text, Ui.Kind kind, Runnable run) {
        JButton b = Ui.button(text, kind);
        b.addActionListener(e -> run.run());
        actions.add(b);
        return b;
    }

    /** A button that works on the selected row. Does nothing when no row is selected. */
    protected JButton onSelected(String text, Ui.Kind kind, Consumer<T> run) {
        return action(text, kind, () -> {
            T t = table.selected();
            if (t != null) run.accept(t);
        });
    }

    protected void addTop(java.awt.Component c) {
        top.add(c);
    }

    protected <R> void call(Async.Call<R> work, Consumer<R> done) {
        Async.run(this, work, done);
    }

    protected void load(Async.Call<? extends List<T>> work) {
        Async.run(this, work::run, table::setRows);
    }
}
