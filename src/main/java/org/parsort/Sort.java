package org.parsort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

public class Sort {

  public static class HeapNode {

    int element;
    int partIndex;
    int nextIndex;

    public HeapNode(int element, int partIndex, int nextIndex) {
      this.element = element;
      this.partIndex = partIndex;
      this.nextIndex = nextIndex;
    }
  }

  public static class Merger extends RecursiveAction {

    private final int[] source;
    private final int[] dest;
    private final int leftStart;
    private final int leftEnd;
    private final int rightStart;
    private final int rightEnd;
    private final int destStart;

    public Merger(int[] source, int[] dest, int destStart, int leftStart,
        int leftEnd,
        int rightStart, int rightEnd) {
      this.source = source;
      this.dest = dest;
      this.leftStart = leftStart;
      this.leftEnd = leftEnd;
      this.rightStart = rightStart;
      this.rightEnd = rightEnd;
      this.destStart = destStart;
    }

    @Override
    protected void compute() {
      int left = leftStart;
      int index = destStart;
      int right = rightStart;
      while (left < leftEnd && right < rightEnd) {
        if (source[left] <= source[right]) {
          dest[index++] = source[left++];
        } else {
          dest[index++] = source[right++];
        }
      }

      while (left < leftEnd) {
        dest[index++] = source[left++];
      }

      while (right < rightEnd) {
        dest[index++] = source[right++];
      }
    }
  }

  public static class ParallelSplitter {

    int[] leftStarts;
    int[] leftEnds;
    int[] rightStarts;
    int[] rightEnds;
    int[] destIndices;

    private final int[] arr;
    private final int sourceLeft;
    private final int sourceMiddle;
    private final int sourceRight;
    private final int cores;

    public ParallelSplitter(int[] arr, int sourceLeft, int sourceMiddle,
        int sourceRight, int cores) {
      this.arr = arr;
      this.sourceLeft = sourceLeft;
      this.sourceMiddle = sourceMiddle;
      this.sourceRight = sourceRight;
      this.cores = cores;

      this.leftStarts = new int[cores];
      this.leftEnds = new int[cores];
      this.rightStarts = new int[cores];
      this.rightEnds = new int[cores];
      this.destIndices = new int[cores];
    }

    public void compute() {
      int L = (int) Math.ceil((sourceMiddle - sourceLeft) / (double) cores);

      int middle = sourceMiddle;
      int index = sourceLeft;
      int left = sourceLeft;

      for (int i = 0; i < cores; i++) {

        int pivot;
        int nextLeft;
        if (left < sourceMiddle) {
          nextLeft = Math.min(sourceLeft + (i + 1) * L, sourceMiddle);
          pivot = arr[nextLeft - 1];
        } else {
          pivot = Integer.MAX_VALUE;
          nextLeft = left;
        }

        int nextMiddle;
        if (nextLeft == sourceMiddle) {
          nextMiddle = sourceRight;
        } else if (middle < sourceRight) {
          nextMiddle = searchRightIndex(arr, pivot, middle, sourceRight - 1);

          while (nextMiddle < sourceRight && arr[nextMiddle] <= pivot) {
            nextMiddle++;
          }
        } else {
          nextMiddle = middle;
        }

        leftStarts[i] = left;
        leftEnds[i] = nextLeft;

        rightStarts[i] = middle;
        rightEnds[i] = nextMiddle;

        destIndices[i] = index;

        index +=
            (leftEnds[i] - leftStarts[i]) + (rightEnds[i] - rightStarts[i]);
        middle = nextMiddle;
        left = nextLeft;
      }
    }

    private int searchRightIndex(int[] arr, int pv, int l, int r) {
      if (l == r) {
        return l;
      } else {
        int m = (int) Math.ceil((l + r) / 2.0);
        if (arr[m] > pv) {
          return searchRightIndex(arr, pv, l, m - 1);
        } else {
          return searchRightIndex(arr, pv, m, r);
        }
      }
    }

  }

  public static void sort(int[] arr) {
    int L1 = 8 * 1024;
    int parts = (int) Math.ceil(arr.length / (double) L1);
    sortSegments(arr, L1, parts);
//    mergeSegmentsByHeap(arr, L1, parts);
    mergePairsBottomUp(arr, L1, parts);
  }

  private static void sortSegments(int[] arr, int L1, int parts) {
    long start1 = System.currentTimeMillis();
    List<ForkJoinTask> tasks = new ArrayList<>();
    for (int i = 0; i < parts; i++) {
      int start = i * L1;
      int end = Math.min((i + 1) * L1, arr.length);
      tasks.add(
          ForkJoinTask.adapt(() -> quickSort(arr, start, end - 1)));
    }
    ForkJoinTask.invokeAll(tasks).forEach(ForkJoinTask::join);
    long end1 = System.currentTimeMillis();
    System.out.println("Sort: " + (end1 - start1) + " ms");
  }

  public static void quickSort(int[] arr, int begin, int end) {
    if (end - begin < 32) {
      for (int i = begin + 1; i <= end; i++) {
        int key = arr[i];
        int j = i - 1;
        while (j >= begin && arr[j] > key) {
          arr[j + 1] = arr[j];
          j = j - 1;
        }
        arr[j + 1] = key;
      }
    } else {
      int pivot = arr[end];
      int i = (begin - 1);

      for (int j = begin; j < end; j++) {
        if (arr[j] <= pivot) {
          i++;

          int swapTemp = arr[i];
          arr[i] = arr[j];
          arr[j] = swapTemp;
        }
      }

      int swapTemp = arr[i + 1];
      arr[i + 1] = arr[end];
      arr[end] = swapTemp;

      int partitionIndex = i + 1;

      quickSort(arr, begin, partitionIndex - 1);
      quickSort(arr, partitionIndex + 1, end);
    }
  }

  private static void mergePairsBottomUp(int[] arr, int L1, int parts) {
    long start2 = System.currentTimeMillis();

    int[] buffer = new int[arr.length];

    System.arraycopy(arr, 0, buffer, 0, arr.length);

    int range = 2;
    int level = 0;

    int[] source;
    int[] target;
    while (range / parts <= 1) {
      long start1 = System.currentTimeMillis();
      List<ForkJoinTask<?>> tasks = new ArrayList<>();

      if (level % 2 == 0) {
        source = arr;
        target = buffer;
      } else {
        source = buffer;
        target = arr;
      }

      for (int i = 0; i < parts; i += range) {
        if (i + (range / 2) >= parts) {
          System.arraycopy(source, i * L1, target, i * L1, arr.length - i * L1);
          break;
        }

        int left = i * L1;
        int middle = (i + range / 2) * L1;
        int right = Math.min((i + range) * L1, arr.length);

//          tasks.add(
//              new Merger(source, target, left, left, middle, middle, right));

        ParallelSplitter sp = new ParallelSplitter(source, left, middle,
            right, 4);
        sp.compute();
        for (int j = 0; j < sp.leftStarts.length; j++) {
          tasks.add(
              new Merger(source, target, sp.destIndices[j], sp.leftStarts[j],
                  sp.leftEnds[j], sp.rightStarts[j], sp.rightEnds[j]));
        }
        
      }

      ForkJoinTask.invokeAll(tasks).forEach(ForkJoinTask::join);
      range <<= 1;
      level++;

      long end1 = System.currentTimeMillis();
      System.out.println("Merge stage: " + (end1 - start1) + " ms");
    }

    if (level % 2 == 1) {
      System.arraycopy(buffer, 0, arr, 0, buffer.length);
    }

    long end2 = System.currentTimeMillis();
    System.out.println("Merge: " + (end2 - start2) + " ms");
  }

  private static void mergeSegmentsByHeap(int[] arr, int L1, int parts) {
    long start2 = System.currentTimeMillis();

    int[] buffer = new int[arr.length];
    int index = 0;

    HeapNode[] heap = new HeapNode[parts];
    int heapTop = 0;
    for (int i = 0; i < parts; i++) {
      heap[heapTop++] = new HeapNode(arr[L1 * i], i, L1 * i + 1);
      heapifyBottomUp(heap, heapTop - 1);
    }

    while (heapTop > 0) {
      HeapNode min = heap[0];
      buffer[index++] = min.element;

      int limit = Math.min(L1 * (min.partIndex + 1), arr.length);
      if (min.nextIndex < limit) {
        heap[0] = new HeapNode(arr[min.nextIndex], min.partIndex,
            min.nextIndex + 1);
      } else {
        heap[0] = heap[heapTop - 1];
        heapTop--;
      }
      heapifyTopDown(heap, heapTop, 0);
    }

    System.arraycopy(buffer, 0, arr, 0, buffer.length);

    long end2 = System.currentTimeMillis();
    System.out.println("Merge by heap: " + (end2 - start2) + " ms");
  }

  private static void heapifyTopDown(HeapNode[] heap, int n, int i) {
    int l = 2 * i;
    int r = 2 * i + 1;

    int smallest = i;

    if (l < n && heap[l].element < heap[smallest].element) {
      smallest = l;
    }

    if (r < n && heap[r].element < heap[smallest].element) {
      smallest = r;
    }

    if (smallest != i) {
      HeapNode t = heap[i];
      heap[i] = heap[smallest];
      heap[smallest] = t;
      heapifyTopDown(heap, n, smallest);
    }
  }

  private static void heapifyBottomUp(HeapNode[] heap, int i) {
    int p = i / 2;

    if (heap[p].element > heap[i].element) {
      HeapNode t = heap[i];
      heap[i] = heap[p];
      heap[p] = t;
      if (p > 0) {
        heapifyBottomUp(heap, p);
      }
    }
  }

}
