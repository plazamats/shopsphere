package com.shopsphere.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;

/** A scrolling grid of cards. The number of columns follows the window width. */
public class GridScroll extends JScrollPane {

    private static final long serialVersionUID = 1L;

    private static final class Holder extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        Holder() { super(new BorderLayout()); setBackground(Theme.BACKGROUND); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 200; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private final Holder holder = new Holder();
    private final int cardWidth;
    private transient List<? extends Component> current = List.of();

    public GridScroll(int cardWidth) {
        this.cardWidth = cardWidth;
        setViewportView(holder);
        setBorder(BorderFactory.createEmptyBorder());
        getViewport().setBackground(Theme.BACKGROUND);
        getVerticalScrollBar().setUnitIncrement(24);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { layoutCards(); }
        });
    }

    public void setCards(List<? extends Component> cards) {
        this.current = cards;
        layoutCards();
    }

    private void layoutCards() {
        int cols = Math.max(1, (getWidth() - 20) / cardWidth);
        JPanel grid = new JPanel(new GridLayout(0, cols, 16, 16));
        grid.setOpaque(false);
        for (Component c : current) grid.add(c);
        // fill the last row so cards keep a fixed size
        int missing = current.isEmpty() ? 0 : (cols - current.size() % cols) % cols;
        for (int i = 0; i < missing; i++) {
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            grid.add(filler);
        }
        holder.removeAll();
        holder.add(grid, BorderLayout.NORTH);
        holder.revalidate();
        holder.repaint();
    }
}
