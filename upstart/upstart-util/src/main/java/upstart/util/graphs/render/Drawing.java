package upstart.util.graphs.render;

import upstart.util.geometry.Point;

interface Drawing<V> {
  Point bottomRight();

  void render(CharGrid grid);

  void visit(Visitor<V> visitor);

  boolean canRemoveRow(int row);

  void removeRows(int firstRow, int count);

  interface Visitor<V> {
    void visit(VertexDrawing<V> vertexDrawing);

    void visit(EdgeDrawing<V> edgeDrawing);
  }
}
