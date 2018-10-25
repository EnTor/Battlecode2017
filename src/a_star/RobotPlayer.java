package a_star;

import static java.lang.Math.*;

import java.util.PriorityQueue;
import java.util.TreeMap;

import battlecode.common.BodyInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Team;
import battlecode.common.TreeInfo;

public class RobotPlayer {
	static final float PI = (float) Math.PI;
	static final boolean DRAWSHIT = true;
	
	static Team enemyTeam = Team.B;
	static RobotController rc;
	static TreeInfo[] ti;

	public static void run(RobotController rcIn) throws GameActionException {
		rc = rcIn;
		if (rc.getTeam() == Team.B) {
			enemyTeam = Team.A;
		}
		switch (rc.getType()) {
		case ARCHON:
			runArchon();
			break;
		case GARDENER:
			runGardener();
			break;
		case SOLDIER:
			runSoldier();
			break;
		case LUMBERJACK:
			runLumberjack();
			break;
		}
	}

	private static void runArchon() throws GameActionException {
		//rc.hireGardener(new Direction(0f)); 
		while (true) {
			try {
//				TreeInfo[] ti = rc.senseNearbyTrees(4);
			
				sensing();
				pathing_target = new MapLocation(335f, 148f);
				System.out.println(pathing_target);
				pathfind();
				System.out.println(pathing_target);
				tryMove(rc.getLocation().directionTo(pathing_target));
				
			Clock.yield();
			} catch (GameActionException e) {
				System.out.println("Archon Exception");
				e.printStackTrace();
			}
		}
	}

	private static void runGardener() throws GameActionException {
		while (true) {
			try {
				sensing();
				pathing_target = new MapLocation(25f, 5f);
				pathfind();//For shrine and sparseTrees map
				
				
				
				//pathfind(new MapLocation(493f, 55f), ti);	//For Pathfinding map
				//pathfind(new MapLocation(284f, 177f), ti);	//For SparseForest map
				Clock.yield();
			} catch (GameActionException e) {
				System.out.println("Archon Exception");
				e.printStackTrace();
			}
		}
	}

	private static void runSoldier() throws GameActionException {
		while (true) {

		}
	}

	private static void runLumberjack() throws GameActionException {
		while (true) {

		}
	}
	
	private static boolean tryMove(Direction dir) throws GameActionException {
		if (rc.canMove(dir)) {
			rc.move(dir);
			return true;
		}
		return false;
	}

	
	private static void sensing() {
		ti = rc.senseNearbyTrees();
		System.out.println("SENSU");
	}

	static final float CURVE_MARGIN = 0.001f;
	static PriorityQueue<PathingPoint> pathingPoints;
	static MapLocation pathing_target;
	private static void pathfind() throws GameActionException {
		PathingPoint pathing = null;
		pathingPoints = new PriorityQueue<>();
		MapLocation location = rc.getLocation();
		float unit_radi = rc.getType().bodyRadius;
		float f_x = pathing_target.x; //final target
		float f_y = pathing_target.y;
		float x = location.x; //current
		float y = location.y;
		float t_x = f_x; //target
		float t_y = f_y;
		
		float naive_dist = (float) hypot(t_x - x, t_y - y);
		//TODO: compare bytycode with rc.getLocation().distancetoofjsiosfjofisofjdoifsjfsifkl	
		PathingPoint start = new PathingPoint(location, 0.0f, naive_dist, PathingPoint.NULL, true, null);
		boolean pass = true;
		for (BodyInfo bi : ti) {
			float b_x = bi.getLocation().x;
			float b_y = bi.getLocation().y;
			float b_r = bi.getRadius() + unit_radi;
			if (line_circle_collision(b_x, b_y, b_r, x, y, t_x, t_y)) {
				pass = false;
			}
		}
		
		if (pass) {
			//pathingPoints.add(new PathingPoint(t_x, t_y, naive_dist, 0, start));
			pathing_target = new MapLocation(t_x, t_y);
			if (DRAWSHIT) {
				rc.setIndicatorLine(rc.getLocation(), pathing_target, 0, 255, 255);
			}
			return;
		}
		// dude points
		for (BodyInfo target : ti) {
			t_x = target.getLocation().x;
			t_y = target.getLocation().y;
			float dist = (float)hypot(t_x - x, t_y - y);
			float radi = target.getRadius() + unit_radi;
			float angle = rc.getLocation().directionTo(target.getLocation()).radians;
			float angle_to_tangent = (float)asin(radi / dist);
			float dist_to_tangent = (float)sqrt(dist*dist - radi*radi);
			// check cw side
			pass = true;
			t_x = x + (float) (dist_to_tangent * cos(angle + angle_to_tangent)) + CURVE_MARGIN;
			t_y = y + (float) (dist_to_tangent * sin(angle + angle_to_tangent)) + CURVE_MARGIN;
			for (BodyInfo bi : ti) {
				if (bi.equals(target)) {
					continue;
				}
				float b_x = bi.getLocation().x;
				float b_y = bi.getLocation().y;
				float b_r = bi.getRadius() + unit_radi;
				if (line_circle_collision(b_x, b_y, b_r, x, y, t_x, t_y)) {
					pass = false;
				}
			}
			if (pass) {
				pathingPoints.add(new PathingPoint(new MapLocation(t_x, t_y), (float) hypot(t_x - x, t_y - y), (float) hypot(f_x-t_x, f_y - t_y), PathingPoint.NULL, true, target));
				if (DRAWSHIT) {
					rc.setIndicatorLine(rc.getLocation(), new MapLocation(t_x, t_y), 255, 41, 0);
				}
				//TODO: fix d to incluce curveshit				
			}
			// check cc side
			pass = true;
			t_x = x + (float) (dist_to_tangent * cos(angle - angle_to_tangent)) - CURVE_MARGIN;
			t_y = y + (float) (dist_to_tangent * sin(angle - angle_to_tangent)) - CURVE_MARGIN;
			for (BodyInfo bi : ti) {
				if (bi.equals(target)) {
					continue;
				}
				float b_x = bi.getLocation().x;
				float b_y = bi.getLocation().y;
				float b_r = bi.getRadius();
				if (line_circle_collision(b_x, b_y, b_r,  x, y,  t_x, t_y)) {
					pass = false;
				}
			}
			if (pass) {
				pathingPoints.add(new PathingPoint(new MapLocation(t_x, t_y), (float) hypot(t_x - x, t_y - y), (float) hypot(f_x-t_x, f_y - t_y), PathingPoint.NULL, false, target));
				if (DRAWSHIT) {
					rc.setIndicatorLine(rc.getLocation(), new MapLocation(t_x, t_y), 255, 41, 0);
				}
				//TODO: fix d to incluce curveshit				
			}
		}
		System.out.println("aswd");
		while (pathing == null || !pathingPoints.isEmpty() && !pathing.equals(pathingPoints.peek())) {
			PathingPoint point = pathingPoints.poll();
			BodyInfo target = point.bi;
			System.out.println("1");
			t_x = target.getLocation().x;
			t_y = target.getLocation().y;
			float dist = (float)hypot(t_x - f_x, t_y - f_y);
			float radi = target.getRadius() + unit_radi;
			float angle = pathing_target.directionTo(target.getLocation()).radians;
			float angle_to_tangent = (float)asin(radi / dist);
			float dist_to_tangent = (float)sqrt(dist*dist - radi*radi);
			System.out.println("2");
			pass = true;
			t_x = f_x + (float) ((dist_to_tangent * cos(angle + angle_to_tangent) + CURVE_MARGIN) * (point.cw ? -1.0f : 1.0f));
			t_y = f_y + (float) ((dist_to_tangent * sin(angle + angle_to_tangent) + CURVE_MARGIN) * (point.cw ? -1.0f : 1.0f));
			for (BodyInfo bi : ti) {
				if (bi.equals(target)) {
					continue;
				}
				float b_x = bi.getLocation().x;
				float b_y = bi.getLocation().y;
				float b_r = bi.getRadius() + unit_radi;
				if (line_circle_collision(b_x, b_y, b_r, f_x, f_y, t_x, t_y)) {
					pass = false;
				}
			}
			System.out.println("3");
			if (pass) {
				PathingPoint p = new PathingPoint(pathing_target, dist_to_tangent, 0.0f, point, true, null);
				pathingPoints.add(p);
				System.out.println("11");
				if (pathing == null || p.compareTo(pathing) <= 0) {
					pathing = p; 
				}
				
				System.out.println("22");
				if (DRAWSHIT) {
					rc.setIndicatorLine(new MapLocation(t_x, t_y), pathing_target, 0, 255, 255);
				}
				System.out.println("33");
				//TODO: fix d to incluce curveshit				
			}
		}
		System.out.println(pathing_target + " : " + pathing.getStart());
		pathing_target = pathing == null ? pathing_target : pathing.getStart();
	}
	
	
	static boolean line_circle_collision(float circle_x, float circle_y, float circle_r, float p1_x, float p1_y, float p2_x, float p2_y) {
		float line_rot = (float) atan2(p1_y - p2_y, p2_x - p1_x);
		float rot = line_rot + PI / 2;
		
		return 	
				// point 1 collision
			hypot(p1_x - circle_x, p1_y - circle_y) <= circle_r
				// point 2 collision
		||  hypot(p2_x - circle_x, p2_y - circle_y) <= circle_r
		
				// is normalizable?
		||  		(circle_x - p1_x) * sin(rot) + (circle_y - p1_y) * cos(rot) >= 0 
				&& 	(circle_x - p2_x) * sin(rot) + (circle_y - p2_y) * cos(rot) <= 0
		
				// line collision
				&& 	abs((circle_x - p1_x) * sin(line_rot) + (circle_y - p1_y) * cos(line_rot)) <= circle_r;
	}
}

class PathingPoint implements Comparable<PathingPoint> {
	static PathingPoint NULL = new PathingPoint() {};
	
	MapLocation loc;
	PathingPoint parent;
	float w, d;
	boolean cw;
	BodyInfo bi;
	
	
	PathingPoint(MapLocation loc, float w, float d, PathingPoint parent, boolean cw, BodyInfo bi) {
		this.loc = loc;
		this.w = parent.w + w; //TODO: curveshit
		this.d = d;
		this.parent = parent;
		this.cw = cw;
		this.bi = bi;
	}
	
	PathingPoint(){}
	
	MapLocation getStart() {
		PathingPoint p = this;
		while (p.parent != NULL) {
			p = p.parent;
		}
		return p.loc;
	}

	@Override
	public int compareTo(PathingPoint o) {
		if (w + d < o.w + o.d) {
			return -1;
		}
		return 1;
	}
}
